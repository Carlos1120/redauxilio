package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.redauxilio.identity.OperatorPermissions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cola e historial privados con HTTP/SQL reales: no ejecuta ni simula decisiones administrativas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationQueueTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OperatorPermissions operators;
  private final List<Long> publications = new ArrayList<>();
  private final List<String> accounts = new ArrayList<>();
  private String operator;

  @BeforeEach
  void operator() {
    operator = account();
    operators.provision(
        accountId(operator),
        operator,
        "GRANT",
        "Equipo de prueba",
        "Operador de fixture",
        UUID.randomUUID().toString());
  }

  @AfterEach
  void cleanup() {
    for (long id : publications) {
      jdbc.update("DELETE FROM publication_moderation_action WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM publication_confirmation_invalidation WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM publication_content_report WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM citizen_publication_change WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM publication_confirmation WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM citizen_publication WHERE id = ?", id);
    }
    for (String email : accounts) {
      long id = accountId(email);
      jdbc.update("DELETE FROM operator_permission_audit WHERE account_id = ?", id);
      jdbc.update("DELETE FROM operator_permission WHERE account_id = ?", id);
      jdbc.update("DELETE FROM citizen_account WHERE id = ?", id);
    }
  }

  @Test
  void operatorKeepsReportRoundsAndAllowsSameReporterToReportAgain() throws Exception {
    long id = publication();
    String reporter = account();
    report(id, reporter);
    report(id);
    report(id);
    decide(id, "KEEP", "Se conserva por corroboración", null, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("SIN_REVISION"));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND active_slot = 1",
            Integer.class,
            id));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND active_slot IS NULL",
            Integer.class,
            id));
    report(id, reporter);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND active_slot = 1",
            Integer.class,
            id));
    mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.reports.length()").value(4))
        .andExpect(jsonPath("$.decisions[0].action").value("KEEP"))
        .andExpect(jsonPath("$.decisions[0].reason").value("Se conserva por corroboración"));
  }

  @Test
  void operatorCanCorrectHideRestoreCloseAndReopenWithPrivateAudit() throws Exception {
    long id = publication();
    jdbc.update("UPDATE citizen_publication SET review_status = 'EN_REVISION' WHERE id = ?", id);
    long version =
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id = ?", Long.class, id);
    decide(
            id,
            "CORRECT",
            "Se precisa la ubicación",
            null,
            "\"correction\":{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Refugio corregido\",\"description\":\"Descripción corregida\",\"location\":\"Zona norte\",\"operationalStatus\":\"DISPONIBLE\",\"impactType\":null,\"latitude\":4.2,\"longitude\":-73.6}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("CORRECT"));
    assertEquals(
        version + 1,
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id = ?", Long.class, id));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ? AND action = 'CORRECT'",
            Integer.class,
            id));
    decide(id, "HIDE", "Ocultar por riesgo concreto", null, null).andExpect(status().isOk());
    decide(id, "RESTORE", "La información ya puede consultarse", null, null)
        .andExpect(status().isOk());
    decide(id, "CLOSE", "Se confirma la finalización", "CERRADO", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
    decide(id, "REOPEN", "La necesidad continúa", "DISPONIBLE", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(false));
    assertEquals(
        5,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_moderation_action WHERE publication_id = ?",
            Integer.class,
            id));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation_invalidation WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void operatorActionsRequireReasonAndRejectInvalidTransitions() throws Exception {
    long id = publication();
    jdbc.update("UPDATE citizen_publication SET review_status = 'EN_REVISION' WHERE id = ?", id);
    decide(id, "HIDE", " ", null, null).andExpect(status().isBadRequest());
    decide(id, "CLOSE", "No terminal", "DISPONIBLE", null).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/operator/publications/{id}/actions", id)
                .with(user("no-es-operador@example.invalid").roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"reason\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/operator/publications/{id}/actions", id)
                .with(user(operator).roles("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"reason\":\"sin token CSRF\"}"))
        .andExpect(status().isForbidden());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_moderation_action WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void failedPrivateAuditRollsBackTheModerationWrite() throws Exception {
    long id = publication();
    jdbc.update("UPDATE citizen_publication SET review_status = 'EN_REVISION' WHERE id = ?", id);
    jdbc.execute(
        "ALTER TABLE publication_moderation_action ADD CONSTRAINT reject_test_reason CHECK (reason <> 'fallo auditoría')");
    try {
      decide(id, "HIDE", "fallo auditoría", null, null).andExpect(status().isInternalServerError());
    } finally {
      jdbc.execute("ALTER TABLE publication_moderation_action DROP CONSTRAINT reject_test_reason");
    }
    assertTrue(
        jdbc.queryForObject(
            "SELECT visible FROM citizen_publication WHERE id = ?", Boolean.class, id));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_moderation_action WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void everyPrivateRouteRejectsVisitorsCitizensAndForgedRoles() throws Exception {
    long id = publication();
    for (String path :
        List.of(
            "/operator",
            "/api/operator/publications",
            "/api/operator/publications/" + id,
            "/api/operator/publications/" + id + "/history/reports?beforeId=10",
            "/api/operator/permissions/history")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(user(operator))).andExpect(status().isForbidden());
      mvc.perform(get(path).with(user("falso@example.invalid").roles("OPERATOR")))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/operator").with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cola de moderación")));
    mvc.perform(get("/moderation.js")).andExpect(status().isOk());
    mvc.perform(post("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/operator/publications").with(user(operator).roles("OPERATOR")).with(csrf()))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(get("/api/operator/permissions/history").with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actor").value("Equipo de prueba"))
        .andExpect(jsonPath("$[0].beforeGranted").value(false))
        .andExpect(jsonPath("$[0].afterGranted").value(true));
  }

  @Test
  void thirdReportEntersQueueWithConfirmedTrustAndHiddenCaseRemainsAuditable() throws Exception {
    long id = publication();
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "INSERT INTO publication_confirmation (publication_id, publication_version, confirmer_id, confirmed_at) VALUES (?, 1, ?, ?)",
          id,
          accountId(account()),
          Timestamp.from(Instant.now()));
    report(id);
    report(id);
    mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items.length()").value(0));
    report(id);
    mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items[0].publication.confidenceLevel").value("CONFIRMADA"))
        .andExpect(jsonPath("$.items[0].publication.reviewStatus").value("EN_REVISION"))
        .andExpect(jsonPath("$.items[0].activeReports").value(3))
        .andExpect(jsonPath("$.items[0].totalReports").value(3));
    jdbc.update("UPDATE citizen_publication SET visible = FALSE, closed = TRUE WHERE id = ?", id);
    mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items[0].visible").value(false))
        .andExpect(jsonPath("$.items[0].publication.closed").value(true));
    String body =
        mvc.perform(
                get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reports.length()").value(3))
            .andExpect(
                jsonPath("$.reports[0].reason").value("Motivo privado <script>prueba</script>"))
            .andExpect(jsonPath("$.history[0].action").value("REVIEW"))
            .andExpect(jsonPath("$.history[0].actorId").isNumber())
            .andExpect(jsonPath("$.context.publication.confirmationCount").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(body.contains("@example.invalid"));
    assertFalse(body.contains("password_hash"));
    assertFalse(body.contains("reporterId"));
    mvc.perform(get("/api/publications/{id}", id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/publications").param("includeClosed", "true"))
        .andExpect(jsonPath("$[?(@.id == %s)]", id).doesNotExist());
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void resolvedReportHistoryIsPreservedWithoutLeakingToPublicOrAuthor() throws Exception {
    long id = publication();
    report(id);
    report(id);
    report(id);
    // Fixture de estado futuro: no existe una API de resolución en este primer bloque.
    jdbc.update(
        "UPDATE publication_content_report SET active_slot = NULL WHERE publication_id = ?", id);
    jdbc.update("UPDATE citizen_publication SET review_status = 'SIN_REVISION' WHERE id = ?", id);
    mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.reports.length()").value(3))
        .andExpect(jsonPath("$.reports[0].active").value(false));
    String publicBody =
        mvc.perform(get("/api/publications/{id}", id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(publicBody.contains("Motivo privado"));
    assertFalse(publicBody.contains("actorId"));
    String author =
        jdbc.queryForObject(
            "SELECT email FROM citizen_account a JOIN citizen_publication p ON p.author_id = a.id WHERE p.id = ?",
            String.class,
            id);
    String history =
        mvc.perform(get("/api/my/publications/{id}/history", id).with(user(author)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(history.contains("Motivo privado"));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void pagesHaveStableOrderAndContextExplicitlyMarksTruncation() throws Exception {
    long first = publication(), second = publication();
    for (long id : List.of(first, second)) {
      report(id);
      report(id);
      report(id);
    }
    mvc.perform(
            get("/api/operator/publications")
                .param("limit", "1")
                .with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items[0].publication.id").value(first))
        .andExpect(jsonPath("$.hasMore").value(true));
    mvc.perform(
            get("/api/operator/publications")
                .param("limit", "1")
                .param("offset", "1")
                .with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items[0].publication.id").value(second))
        .andExpect(jsonPath("$.hasMore").value(false));
    long reporter = accountId(account());
    for (int i = 0; i < 51; i++) {
      jdbc.update(
          "INSERT INTO publication_content_report (publication_id, reporter_id, reason, reported_at, active_slot) VALUES (?, ?, 'Histórico ficticio', ?, NULL)",
          first,
          reporter,
          Timestamp.from(Instant.now()));
      jdbc.update(
          "INSERT INTO citizen_publication_change (publication_id, actor_id, action, before_snapshot, after_snapshot, reason, changed_at) VALUES (?, ?, 'EDIT', '{}', '{}', 'Contexto ficticio', ?)",
          first,
          reporter,
          Timestamp.from(Instant.now()));
    }
    mvc.perform(
            get("/api/operator/publications/{id}", first).with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.reports.length()").value(50))
        .andExpect(jsonPath("$.moreReports").value(true))
        .andExpect(jsonPath("$.history.length()").value(50))
        .andExpect(jsonPath("$.moreHistory").value(true));
  }

  @Test
  void operatorCanPageOlderPrivateHistoryWithoutRepeatingRecentEntries() throws Exception {
    long id = publication();
    jdbc.update("UPDATE citizen_publication SET review_status = 'EN_REVISION' WHERE id = ?", id);
    long reporter = accountId(account());
    Timestamp time = Timestamp.from(Instant.now());
    for (int i = 0; i < 52; i++) {
      jdbc.update(
          "INSERT INTO publication_content_report (publication_id, reporter_id, reason, reported_at, active_slot) VALUES (?, ?, ?, ?, NULL)",
          id,
          reporter,
          "Denuncia ficticia " + i,
          time);
      jdbc.update(
          "INSERT INTO citizen_publication_change (publication_id, actor_id, action, before_snapshot, after_snapshot, reason, changed_at) VALUES (?, ?, 'EDIT', '{}', '{}', ?, ?)",
          id,
          reporter,
          "Cambio ficticio " + i,
          time);
      jdbc.update(
          "INSERT INTO publication_moderation_action (publication_id, actor_id, action, before_snapshot, after_snapshot, reason, acted_at) VALUES (?, ?, 'HIDE', '{}', '{}', ?, ?)",
          id,
          accountId(operator),
          "Decisión ficticia " + i,
          time);
    }
    mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports.length()").value(50))
        .andExpect(jsonPath("$.moreReports").value(true))
        .andExpect(jsonPath("$.history.length()").value(50))
        .andExpect(jsonPath("$.moreHistory").value(true))
        .andExpect(jsonPath("$.decisions.length()").value(50))
        .andExpect(jsonPath("$.moreDecisions").value(true));

    for (String stream : List.of("reports", "changes", "decisions")) {
      String table =
          switch (stream) {
            case "reports" -> "publication_content_report";
            case "changes" -> "citizen_publication_change";
            default -> "publication_moderation_action";
          };
      List<Long> recentIds =
          jdbc.queryForList(
              "SELECT id FROM " + table + " WHERE publication_id = ? ORDER BY id DESC LIMIT 50",
              Long.class,
              id);
      mvc.perform(
              get("/api/operator/publications/{id}/history/{stream}", id, stream)
                  .param("beforeId", recentIds.get(recentIds.size() - 1).toString())
                  .with(user(operator).roles("OPERATOR")))
          .andExpect(status().isOk())
          .andExpect(
              header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
          .andExpect(jsonPath("$.stream").value(stream))
          .andExpect(jsonPath("$.items.length()").value(2))
          .andExpect(jsonPath("$.items[0].id").value(recentIds.get(49) - 1))
          .andExpect(jsonPath("$.hasMore").value(false));
    }
    mvc.perform(
            get("/api/operator/publications/{id}/history/unknown", id)
                .param("beforeId", "1")
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isBadRequest());
    for (String cursor : List.of("0", "-1"))
      mvc.perform(
              get("/api/operator/publications/{id}/history/reports", id)
                  .param("beforeId", cursor)
                  .with(user(operator).roles("OPERATOR")))
          .andExpect(status().isBadRequest());
    for (String limit : List.of("0", "51"))
      mvc.perform(
              get("/api/operator/publications/{id}/history/reports", id)
                  .param("beforeId", "1")
                  .param("limit", limit)
                  .with(user(operator).roles("OPERATOR")))
          .andExpect(status().isBadRequest());
  }

  @Test
  void invalidPagesAndMissingContextsFailClearlyWithoutWriting() throws Exception {
    for (String path : List.of("/api/operator/publications", "/api/operator/permissions/history")) {
      for (String limit : List.of("0", "51", "-1", "abc"))
        mvc.perform(get(path).param("limit", limit).with(user(operator).roles("OPERATOR")))
            .andExpect(status().isBadRequest());
      for (String offset : List.of("-1", "10001"))
        mvc.perform(get(path).param("offset", offset).with(user(operator).roles("OPERATOR")))
            .andExpect(status().isBadRequest());
    }
    long ordinary = publication();
    for (long id : List.of(1L, Long.MAX_VALUE, ordinary))
      mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
          .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/operator/publications/{id}/history/reports", ordinary)
                .param("beforeId", "1")
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isNotFound());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ?",
            Integer.class,
            ordinary));
  }

  /**
   * RN-19: un oculto conserva auditoría aunque no tenga denuncias; RF-16 admite revisión manual
   * futura.
   */
  @Test
  void hiddenOrManuallyReviewedPublicationWithoutReportsIsStillAuditable() throws Exception {
    long id = publication();
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", id);
    mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.context.visible").value(false))
        .andExpect(jsonPath("$.reports.length()").value(0));
    jdbc.update(
        "UPDATE citizen_publication SET visible = TRUE, review_status = 'EN_REVISION' WHERE id = ?",
        id);
    mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
        .andExpect(jsonPath("$.items[0].activeReports").value(0));
    mvc.perform(get("/api/operator/publications/{id}", id).with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reports.length()").value(0));
  }

  @Test
  void dependencyFailureIsSanitizedAndIncludesCorrelationReference() throws Exception {
    jdbc.execute("ALTER TABLE publication_content_report RENAME TO ra08_reports_backup");
    try {
      String result =
          mvc.perform(get("/api/operator/publications").with(user(operator).roles("OPERATOR")))
              .andExpect(status().isInternalServerError())
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(
                  jsonPath("$.reference")
                      .value(org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(result.contains("SELECT"));
      assertFalse(result.contains("Jdbc"));
      assertFalse(result.contains("publication_content_report"));
    } finally {
      jdbc.execute("ALTER TABLE ra08_reports_backup RENAME TO publication_content_report");
    }
  }

  private String account() {
    String email = "ra08-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Prueba cola', 'sin-credencial-usable', ?)",
        email,
        Timestamp.from(Instant.now()));
    accounts.add(email);
    return email;
  }

  private long accountId(String email) {
    return jdbc.queryForObject("SELECT id FROM citizen_account WHERE email = ?", Long.class, email);
  }

  private long publication() {
    long author = accountId(account());
    jdbc.update(
        "INSERT INTO citizen_publication (author_id, category, assistance_subtype, title, description, location, operational_status, latitude, longitude, created_at, updated_at) VALUES (?, 'ASSISTANCE', 'SHELTER', 'Caso ficticio RA08', 'Descripción ficticia', 'Zona ficticia', 'DISPONIBLE', 4.15, -73.63, ?, ?)",
        author,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    long id =
        jdbc.queryForObject(
            "SELECT id FROM citizen_publication WHERE author_id = ?", Long.class, author);
    publications.add(id);
    return id;
  }

  private void report(long id) throws Exception {
    report(id, account());
  }

  private void report(long id, String reporter) throws Exception {
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(user(reporter))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo privado <script>prueba</script>\"}"))
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions decide(
      long id, String action, String reason, String status, String extra) throws Exception {
    String body =
        "{\"action\":\""
            + action
            + "\",\"reason\":\""
            + reason
            + "\""
            + (status == null ? "" : ",\"operationalStatus\":\"" + status + "\"")
            + (extra == null ? "" : "," + extra)
            + "}";
    return mvc.perform(
        post("/api/operator/publications/{id}/actions", id)
            .with(user(operator).roles("OPERATOR"))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}

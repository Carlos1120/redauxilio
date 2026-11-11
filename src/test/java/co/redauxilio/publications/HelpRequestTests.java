package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.redauxilio.identity.OperatorPermissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** A1–A6: flujos HTTP reales y SQL en H2 exclusiva; no es aceptación humana ni producción. */
@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:ra11help;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(PublicationFreshnessTests.TimeConfig.class)
class HelpRequestTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PublicationFreshnessTests.TestClock clock;
  @Autowired OperatorPermissions operators;
  private String author;
  private static final Instant START = Instant.parse("2026-11-02T12:00:00Z");

  @BeforeEach
  void fixtures() {
    // Solo la H2 mem:ra11help configurada arriba; cada prueba parte sin datos de otra ejecución.
    for (String table :
        List.of(
            "publication_moderation_action",
            "publication_confirmation_invalidation",
            "publication_confirmation",
            "publication_content_report",
            "citizen_publication_change",
            "citizen_publication",
            "operator_permission_audit",
            "operator_permission",
            "citizen_account")) jdbc.update("DELETE FROM " + table);
    clock.now = START;
    author = account();
  }

  String account() {
    String email = "ra11-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email,display_name,password_hash,created_at) VALUES (?,'Ficticio','no-usable',?)",
        email,
        Timestamp.from(clock.now));
    return email;
  }

  long accountId(String email) {
    return jdbc.queryForObject("SELECT id FROM citizen_account WHERE email=?", Long.class, email);
  }

  Map<String, Object> body() {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("category", "HELP_REQUEST");
    b.put("title", "Agua para comunidad ficticia");
    b.put("description", "Se solicita agua para una comunidad de prueba");
    b.put("location", "Sector público ficticio");
    b.put("latitude", 4.15);
    b.put("longitude", -73.63);
    b.put("needType", "AGUA");
    b.put("declaredUrgency", "MEDIA");
    b.put("requesterReference", "Comunidad de prueba");
    b.put("approximateQuantity", "20 familias");
    return b;
  }

  long create() throws Exception {
    String out =
        mvc.perform(
                post("/api/publications")
                    .with(user(author))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.operationalStatus").value("ABIERTA"))
            .andExpect(jsonPath("$.needType").value("AGUA"))
            .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"))
            .andExpect(jsonPath("$.authorId").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(out).path("id").asLong();
  }

  void transition(long id, String state, String reason, int expected) throws Exception {
    mvc.perform(
            post("/api/my/publications/{id}/status", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(Map.of("operationalStatus", state, "reason", reason))))
        .andExpect(status().is(expected));
  }

  @Test
  void createValidateLimitsAndNoPartialRows() throws Exception {
    for (String field :
        List.of(
            "needType",
            "declaredUrgency",
            "requesterReference",
            "title",
            "description",
            "location")) {
      var b = body();
      b.put(field, " ");
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isBadRequest());
    }
    for (var entry :
        Map.<String, Object>of(
                "needType",
                "DESCONOCIDA",
                "declaredUrgency",
                "URGENTE",
                "requesterReference",
                "correo@example.invalid",
                "latitude",
                91,
                "operationalStatus",
                "EN ATENCIÓN",
                "approximateQuantity",
                "x".repeat(121))
            .entrySet()) {
      var b = body();
      b.put(entry.getKey(), entry.getValue());
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isBadRequest());
    }
    var phone = body();
    phone.put("requesterReference", "Contacto 3001234567");
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(phone)))
        .andExpect(status().isBadRequest());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class));
    var optional = body();
    optional.remove("approximateQuantity");
    optional.put("needType", "OTRO");
    optional.put("requesterReference", "x".repeat(120));
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(optional)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.approximateQuantity").doesNotExist());
    for (int i = 0; i < 4; i++) {
      var separated = body();
      separated.put("latitude", 20 + i * 0.02);
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(separated)))
          .andExpect(status().isCreated());
    }
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body())))
        .andExpect(status().isTooManyRequests());
  }

  /** Catálogos autorizados y límites de los cuatro campos propios. */
  @ParameterizedTest
  @ValueSource(strings = {"AGUA", "ALIMENTOS", "REFUGIO", "SALUD", "TRANSPORTE", "OTRO"})
  void allowedNeedsAndTextBounds(String need) throws Exception {
    var b = body();
    b.put("needType", need);
    b.put("approximateQuantity", "x".repeat(120));
    b.put("requesterReference", "x".repeat(120));
    b.put("declaredUrgency", "BAJA");
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(b)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.needType").value(need));
    for (String key : List.of("approximateQuantity", "requesterReference")) {
      var invalid = body();
      invalid.put(key, "x".repeat(121));
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "ABIERTA,EN ATENCIÓN,true",
    "ABIERTA,SATISFECHA,true",
    "EN ATENCIÓN,ABIERTA,true",
    "EN ATENCIÓN,SATISFECHA,true",
    "SATISFECHA,ABIERTA,true",
    "SATISFECHA,EN ATENCIÓN,false",
    "ABIERTA,ABIERTA,false",
    "ABIERTA,CERRADO,false",
    "ABIERTA,HABILITADA,false",
    "EN ATENCIÓN,EN ATENCIÓN,false",
    "SATISFECHA,SATISFECHA,false"
  })
  void stateMatrixAndReopening(String from, String to, boolean accepted) throws Exception {
    long id = create();
    jdbc.update(
        "UPDATE citizen_publication SET operational_status=?,closed=? WHERE id=?",
        from,
        from.equals("SATISFECHA"),
        id);
    if (from.equals("SATISFECHA") && accepted) transition(id, to, "", 400);
    transition(id, to, "Motivo de prueba", accepted ? 200 : 400);
    assertEquals(
        accepted ? to : from,
        jdbc.queryForObject(
            "SELECT operational_status FROM citizen_publication WHERE id=?", String.class, id));
    assertEquals(
        accepted ? to.equals("SATISFECHA") : from.equals("SATISFECHA"),
        jdbc.queryForObject(
            "SELECT closed FROM citizen_publication WHERE id=?", Boolean.class, id));
    assertEquals(
        accepted ? 1 : 0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id=?",
            Integer.class,
            id));
    if (accepted) {
      var event =
          jdbc.queryForMap("SELECT * FROM citizen_publication_change WHERE publication_id=?", id);
      assertEquals(accountId(author), ((Number) event.get("actor_id")).longValue());
      assertNotNull(event.get("changed_at"));
      assertTrue(event.get("before_snapshot").toString().contains("needType"));
    }
  }

  @Test
  void editNoOpVersionHistoryAndValidity() throws Exception {
    long id = create();
    var edit = body();
    edit.put("operationalStatus", "ABIERTA");
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/publications/{id}/confirmations", id)
                  .with(user(account()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
          .andExpect(status().isOk());
    clock.now = START.plus(Duration.ofHours(24));
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"));
    clock.now = clock.now.plusNanos(1);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("DESACTUALIZADA"))
        .andExpect(jsonPath("$.confirmationCount").value(3));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication_change", Integer.class));
    edit.put("needType", "ALIMENTOS");
    edit.put("approximateQuantity", "30 familias");
    edit.put("declaredUrgency", "ALTA");
    edit.put("requesterReference", "Comunidad actualizada");
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needType").value("ALIMENTOS"))
        .andExpect(jsonPath("$.confirmationCount").value(0))
        .andExpect(jsonPath("$.confirmationVersion").value(2))
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"));
    assertEquals(
        3, jdbc.queryForObject("SELECT COUNT(*) FROM publication_confirmation", Integer.class));
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(author)))
        .andExpect(
            jsonPath("$[0].beforeSnapshot").value(org.hamcrest.Matchers.containsString("AGUA")))
        .andExpect(
            jsonPath("$[0].afterSnapshot")
                .value(org.hamcrest.Matchers.containsString("30 familias")));
  }

  @Test
  void permissionsClosedHiddenAndFailedAudit() throws Exception {
    long id = create();
    var edit = body();
    edit.put("operationalStatus", "ABIERTA");
    edit.put("needType", "ALIMENTOS");
    mvc.perform(
            post("/api/publications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body())))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(account()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isForbidden());
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT ra11_edit_rollback CHECK (action <> 'EDIT')");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () ->
              mvc.perform(
                  patch("/api/my/publications/{id}", id)
                      .with(user(author))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(edit))));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT ra11_edit_rollback");
    }
    assertEquals(
        "AGUA",
        jdbc.queryForObject(
            "SELECT need_type FROM citizen_publication WHERE id=?", String.class, id));
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT ra11_rollback CHECK (action <> 'CLOSE')");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class, () -> transition(id, "SATISFECHA", "", 500));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT ra11_rollback");
    }
    assertEquals(
        "ABIERTA",
        jdbc.queryForObject(
            "SELECT operational_status FROM citizen_publication WHERE id=?", String.class, id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id=?", Integer.class, id));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication_change", Integer.class));
    transition(id, "SATISFECHA", "", 200);
    mvc.perform(get("/api/publications").param("category", "HELP_REQUEST"))
        .andExpect(jsonPath("$.length()").value(1)); // solo ejemplo ficticio abierto
    mvc.perform(
            get("/api/publications")
                .param("category", "HELP_REQUEST")
                .param("includeClosed", "true"))
        .andExpect(jsonPath("$.length()").value(2));
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isConflict());
    jdbc.update("UPDATE citizen_publication SET visible=FALSE WHERE id=?", id);
    mvc.perform(get("/api/publications/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void reportingModerationAndPublicMappingRemainIndependent() throws Exception {
    long id = create();
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/publications/{id}/content-reports", id)
                  .with(user(account()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"Información incorrecta\"}"))
          .andExpect(status().isOk());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.reviewStatus").value("EN_REVISION"))
        .andExpect(jsonPath("$.declaredUrgency").value("MEDIA"));
    String operator = account();
    operators.provision(
        accountId(operator),
        operator,
        "GRANT",
        "Fixture",
        "Operador de prueba",
        UUID.randomUUID().toString());
    var correction = body();
    correction.put("operationalStatus", "ABIERTA");
    correction.put("needType", "SALUD");
    mvc.perform(
            post("/api/operator/publications/{id}/actions", id)
                .with(user(operator).roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "action",
                            "CORRECT",
                            "reason",
                            "Corrección de prueba",
                            "correction",
                            correction))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.needType").value("SALUD"))
        .andExpect(jsonPath("$.reviewStatus").value("SIN_REVISION"));
    for (String action : List.of("HIDE", "RESTORE"))
      mvc.perform(
              post("/api/operator/publications/{id}/actions", id)
                  .with(user(operator).roles("OPERATOR"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json.writeValueAsString(
                          Map.of("action", action, "reason", "Visibilidad de prueba"))))
          .andExpect(status().isOk());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.needType").value("SALUD"))
        .andExpect(jsonPath("$.declaredUrgency").value("MEDIA"));
    for (var transition :
        List.of(
            Map.of(
                "action", "CLOSE", "reason", "Cierre ficticio", "operationalStatus", "SATISFECHA"),
            Map.of(
                "action",
                "REOPEN",
                "reason",
                "Reapertura ficticia",
                "operationalStatus",
                "ABIERTA")))
      mvc.perform(
              post("/api/operator/publications/{id}/actions", id)
                  .with(user(operator).roles("OPERATOR"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(transition)))
          .andExpect(status().isOk());
    mvc.perform(get("/api/publications/1")).andExpect(jsonPath("$.needType").doesNotExist());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.requesterReference").value("Comunidad de prueba"))
        .andExpect(jsonPath("$.confirmationVersion").value(4));
  }
}

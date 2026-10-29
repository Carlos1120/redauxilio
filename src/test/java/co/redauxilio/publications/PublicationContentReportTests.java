package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Regresiones RA-07 con HTTP/SQL reales y cuentas sintéticas; no son aceptación humana. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationContentReportTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  private final List<String> accounts = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM publication_content_report WHERE reporter_id IN (SELECT id FROM citizen_account WHERE email = ?) OR publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM citizen_publication_change WHERE publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?)",
          email);
      jdbc.update(
          "DELETE FROM publication_confirmation WHERE publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?)",
          email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
  }

  @Test
  void zeroTwoThreeReportsPreserveCommunityTrustVisibilityAndStatus() throws Exception {
    String author = account();
    long id = publication(author);
    // Respaldo independiente previo: denunciar no lo invalida ni lo convierte en revisión de
    // confianza.
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "INSERT INTO publication_confirmation (publication_id, publication_version, confirmer_id, confirmed_at) VALUES (?, 1, (SELECT id FROM citizen_account WHERE email = ?), ?)",
          id,
          account(),
          Timestamp.from(Instant.now()));
    assertState(id, "SIN_REVISION", "CONFIRMADA");
    String first = account();
    submit(id, first, "Motivo privado uno", 200);
    submit(id, first, "Duplicado", 409);
    submit(id, author, "No cuenta el autor", 403);
    submit(id, account(), "Motivo privado dos", 200);
    assertState(id, "SIN_REVISION", "CONFIRMADA");
    String third = account();
    submit(id, third, "Motivo privado tres", 200);
    assertState(id, "EN_REVISION", "CONFIRMADA");
    mvc.perform(get("/api/publications")).andExpect(jsonPath("$[?(@.id == %s)]", id).exists());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(3))
        .andExpect(jsonPath("$.operationalStatus").value("DISPONIBLE"))
        .andExpect(jsonPath("$.closed").value(false));
    assertEquals(3, rows(id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ? AND action = 'REVIEW'",
            Integer.class,
            id));
    assertEquals(
        "EN_REVISION",
        json.readTree(
                jdbc.queryForObject(
                    "SELECT after_snapshot FROM citizen_publication_change WHERE publication_id = ?",
                    String.class,
                    id))
            .path("reviewStatus")
            .asText());
    submit(id, account(), "Cuarto motivo", 200);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void securityCsrfValidationAndPrivateReasonsCannotLeak() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Privado\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(user(citizen))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Privado\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(user(citizen))
                .with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Privado\"}"))
        .andExpect(status().isForbidden());
    for (String reason : List.of("", " \n\t", "x".repeat(501))) submit(id, citizen, reason, 400);
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    submit(id, "inexistente@example.invalid", "Motivo", 401);
    submit(1, citizen, "Ejemplo", 404);
    submit(Long.MAX_VALUE, citizen, "Ausente", 404);
    submit(id, citizen, "x".repeat(500), 200);
    for (int i = 0; i < 2; i++)
      submit(id, account(), "Razón privada <script>alert('x')</script>", 200);
    for (String path : List.of("/api/publications", "/api/publications/" + id)) {
      String body =
          mvc.perform(get(path))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(body.contains("reporter"));
      assertFalse(body.contains("reason"));
      assertFalse(body.contains("@example.invalid"));
      assertFalse(body.contains("<script>"));
    }
    String ownHistory =
        mvc.perform(get("/api/my/publications/{id}/history", id).with(user(author)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(ownHistory.contains("Razón privada"));
    assertFalse(ownHistory.contains("@example.invalid"));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND reported_at IS NOT NULL AND reporter_id IS NOT NULL",
            Integer.class,
            id));
  }

  @Test
  void hiddenIsRejectedButBothClosedRepresentationsRemainReportable() throws Exception {
    String author = account(), citizen = account();
    long hidden = publication(author);
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", hidden);
    submit(hidden, citizen, "Oculto", 404);
    long closed = publication(author);
    jdbc.update(
        "UPDATE citizen_publication SET closed = TRUE, operational_status = 'CERRADO' WHERE id = ?",
        closed);
    submit(closed, citizen, "Contenido cuestionable aunque cerró", 200);
    mvc.perform(get("/api/publications/{id}", closed)).andExpect(jsonPath("$.closed").value(true));
    long legacyRoad = publication(author);
    jdbc.update(
        "UPDATE citizen_publication SET category = 'ROAD', assistance_subtype = NULL, operational_status = 'HABILITADA', impact_type = 'DERRUMBE', closed = FALSE WHERE id = ?",
        legacyRoad);
    submit(legacyRoad, citizen, "Vía habilitada todavía visible", 200);
    mvc.perform(get("/api/publications/{id}", legacyRoad))
        .andExpect(jsonPath("$.closed").value(true));
    assertEquals(
        false,
        jdbc.queryForObject(
            "SELECT closed FROM citizen_publication WHERE id = ?", Boolean.class, legacyRoad));
    assertEquals(0, rows(hidden));
  }

  @Test
  void failuresInInsertReviewAndAuditRollBackTheWholeOperation() throws Exception {
    String author = account();
    long id = publication(author);
    jdbc.execute(
        "ALTER TABLE publication_content_report ADD CONSTRAINT test_ra07_insert CHECK (publication_id <> "
            + id
            + ")");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () -> submit(id, account(), "Fallo de inserción", 200));
    } finally {
      jdbc.execute("ALTER TABLE publication_content_report DROP CONSTRAINT test_ra07_insert");
    }
    assertEquals(0, rows(id));
    submit(id, account(), "Primero", 200);
    submit(id, account(), "Segundo", 200);
    jdbc.execute(
        "ALTER TABLE citizen_publication ADD CONSTRAINT test_ra07_state CHECK (id <> "
            + id
            + " OR review_status <> 'EN_REVISION')");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () -> submit(id, account(), "No persiste por fallo de estado", 200));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication DROP CONSTRAINT test_ra07_state");
    }
    assertEquals(2, rows(id));
    assertState(id, "SIN_REVISION", "REPORTADA");
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT test_ra07_audit CHECK (publication_id <> "
            + id
            + ")");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () -> submit(id, account(), "No persiste por fallo de auditoría", 200));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT test_ra07_audit");
    }
    assertEquals(2, rows(id));
    assertState(id, "SIN_REVISION", "REPORTADA");
    submit(id, account(), "Tercero confirmado", 200);
    assertState(id, "EN_REVISION", "REPORTADA");
  }

  @Test
  void simultaneousIndependentReportsReachThresholdOnceAndDuplicateCountsOnce() throws Exception {
    String author = account(), first = account(), second = account(), third = account();
    long duplicate = publication(author);
    assertEquals(
        List.of(200, 409),
        parallel(
            List.of(
                () -> code(duplicate, first, "Uno"), () -> code(duplicate, first, "Duplicado"))));
    assertEquals(1, rows(duplicate));
    long id = publication(author);
    assertEquals(
        List.of(200, 200, 200),
        parallel(
            List.of(
                () -> code(id, first, "Uno"),
                () -> code(id, second, "Dos"),
                () -> code(id, third, "Tres"))));
    assertState(id, "EN_REVISION", "REPORTADA");
    assertEquals(3, rows(id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ?",
            Integer.class,
            id));
  }

  @Test
  void uniqueActiveSlotPreservesInactiveHistoryForFutureResolution() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    submit(id, citizen, "Original", 200);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO publication_content_report (publication_id, reporter_id, reason, reported_at) SELECT publication_id, reporter_id, 'Duplicado SQL', reported_at FROM publication_content_report WHERE publication_id = ?",
                id));
    // Emulación SQL exclusiva de pruebas; RA-07 no expone operaciones administrativas de
    // resolución.
    for (int i = 0; i < 2; i++) {
      jdbc.update(
          "UPDATE publication_content_report SET active_slot = NULL WHERE publication_id = ?", id);
      submit(id, citizen, "Nuevo activo " + i, 200);
    }
    assertEquals(3, rows(id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND active_slot = 1",
            Integer.class,
            id));
    assertState(id, "SIN_REVISION", "REPORTADA");
  }

  @Test
  void reporterIdentityComesOnlyFromSessionAndPrivateHistoryIsAttributed() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    mvc.perform(
            post("/api/publications/{id}/content-reports", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"reason\":\"Motivo privado\",\"reporterId\":1,\"email\":\"otro@example.invalid\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    assertEquals(
        citizen,
        jdbc.queryForObject(
            "SELECT a.email FROM publication_content_report r JOIN citizen_account a ON a.id = r.reporter_id WHERE r.publication_id = ?",
            String.class,
            id));
    submit(id, citizen, "No cambia identidad al reintentar", 409);
    assertEquals(1, rows(id));
  }

  private List<Integer> parallel(List<Callable<Integer>> operations) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(operations.size());
    var ready = new CountDownLatch(operations.size());
    var start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (Callable<Integer> operation : operations)
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Sin inicio concurrente");
                  return operation.call();
                }));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> result = new ArrayList<>();
      for (Future<Integer> future : futures) result.add(future.get(10, TimeUnit.SECONDS));
      result.sort(Integer::compareTo);
      return result;
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }

  private void assertState(long id, String review, String confidence) throws Exception {
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value(review))
        .andExpect(jsonPath("$.confidenceLevel").value(confidence));
  }

  private int rows(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ?",
        Integer.class,
        id);
  }

  private void submit(long id, String email, String reason, int expected) throws Exception {
    assertEquals(expected, code(id, email, reason));
  }

  private int code(long id, String email, String reason) throws Exception {
    var response =
        mvc.perform(
                post("/api/publications/{id}/content-reports", id)
                    .with(user(email))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("reason", reason))))
            .andReturn()
            .getResponse();
    if (response.getStatus() == 200) assertEquals("no-store", response.getHeader("Cache-Control"));
    return response.getStatus();
  }

  private String account() {
    String email = "ra07-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Prueba RA07', 'hash-no-usado', ?)",
        email,
        Timestamp.from(Instant.now()));
    accounts.add(email);
    return email;
  }

  private long publication(String author) {
    jdbc.update(
        "INSERT INTO citizen_publication (author_id, category, assistance_subtype, title, description, location, operational_status, latitude, longitude, created_at, updated_at) VALUES ((SELECT id FROM citizen_account WHERE email = ?), 'ASSISTANCE', 'SHELTER', 'Punto RA07', 'Descripción', 'Sector público', 'DISPONIBLE', 4.15, -73.63, ?, ?)",
        author,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM citizen_publication WHERE author_id = (SELECT id FROM citizen_account WHERE email = ?)",
        Long.class,
        author);
  }
}

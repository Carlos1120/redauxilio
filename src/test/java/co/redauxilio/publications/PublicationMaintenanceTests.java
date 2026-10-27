package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Pruebas HTTP+SQL reales: permisos, transiciones y auditoría persistida. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationMaintenanceTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  private final List<String> accounts = new ArrayList<>();

  @AfterEach
  void cleanOwnData() {
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM citizen_publication_change WHERE publication_id IN"
              + " (SELECT p.id FROM citizen_publication p JOIN citizen_account a"
              + " ON a.id = p.author_id WHERE a.email = ?)",
          email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN"
              + " (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
  }

  @Test
  void authenticatedAuthorCanEditButNotAnotherCitizenOrAnonymous() throws Exception {
    String owner = account();
    String stranger = account();
    long id = publish(owner, false);
    mvc.perform(get("/api/my/publications")).andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Nuevo")))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Nuevo")))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(stranger))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Nuevo")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Nuevo")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Nuevo"));
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("EDIT"));
    mvc.perform(get("/api/my/publications").with(user(stranger)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    assertEquals(1, historyCount(id));
  }

  @Test
  void pointCloseReopenAndInvalidEditPreserveStateAndHistory() throws Exception {
    String owner = account();
    long id = publish(owner, false);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Nuevo").replace("4.15", "100.0")))
        .andExpect(status().isBadRequest());
    assertEquals(0, historyCount(id));
    transition(owner, id, "CERRADO", "", 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
    mvc.perform(get("/api/publications").param("category", "ASSISTANCE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == %s)]", id).doesNotExist());
    mvc.perform(get("/api/publications").param("includeClosed", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == %s)]", id).exists());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Edición prohibida")))
        .andExpect(status().isConflict());
    transition(owner, id, "LLENO", "", 400);
    transition(owner, id, "DISPONIBLE", "", 400);
    assertEquals(1, historyCount(id));
    transition(owner, id, "DISPONIBLE", "Se confirmó reapertura", 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(false));
    assertEquals(2, historyCount(id));
  }

  @Test
  void roadReopensWithReasonAndRejectsStateFromOtherCategory() throws Exception {
    String owner = account();
    long id = publish(owner, true);
    transition(owner, id, "HABILITADA", "", 200);
    transition(owner, id, "LLENO", "Motivo", 400);
    transition(owner, id, "PARCIALMENTE HABILITADA", "Volvió el daño", 200);
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("CLOSE"))
        .andExpect(jsonPath("$[1].action").value("REOPEN"));
    assertEquals(2, historyCount(id));
  }

  @Test
  void anonymousMaintenanceScriptIsJavaScript() throws Exception {
    mvc.perform(get("/my-publications.js"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith("text/javascript"));
  }

  @Test
  void initiallyEnabledRoadIsClosedAndRequiresMotivatedReopening() throws Exception {
    String owner = account();
    String response =
        mvc.perform(
                post("/api/publications")
                    .with(user(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(road("HABILITADA")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(response).path("id").asLong();
    mvc.perform(get("/api/publications/{id}", id)).andExpect(jsonPath("$.closed").value(true));
    mvc.perform(get("/api/publications"))
        .andExpect(jsonPath("$[?(@.id == %s)]", id).doesNotExist());
    transition(owner, id, "BLOQUEADA", "", 400);
    transition(owner, id, "BLOQUEADA", "Nuevo daño comprobado", 200);
  }

  private static String road(String state) {
    return """
        {"category":"ROAD","title":"Vía prueba","description":"Daño reciente",
        "location":"Villavicencio","operationalStatus":"%s","impactType":"DERRUMBE",
        "latitude":4.15,"longitude":-73.63}
        """
        .formatted(state);
  }

  @Test
  void allStatePairsRespectTheAgreedMatricesAndAuditOnlyAllowedChanges() throws Exception {
    java.util.Map<String, java.util.Set<String>> pointMatrix =
        java.util.Map.of(
            "DISPONIBLE", java.util.Set.of("CAPACIDAD LIMITADA", "LLENO", "CERRADO"),
            "CAPACIDAD LIMITADA", java.util.Set.of("DISPONIBLE", "LLENO", "CERRADO"),
            "LLENO", java.util.Set.of("DISPONIBLE", "CAPACIDAD LIMITADA", "CERRADO"),
            "CERRADO", java.util.Set.of("DISPONIBLE", "CAPACIDAD LIMITADA"));
    java.util.Map<String, java.util.Set<String>> roadMatrix =
        java.util.Map.of(
            "BLOQUEADA", java.util.Set.of("PARCIALMENTE HABILITADA", "HABILITADA"),
            "PARCIALMENTE HABILITADA", java.util.Set.of("BLOQUEADA", "HABILITADA"),
            "HABILITADA", java.util.Set.of("BLOQUEADA", "PARCIALMENTE HABILITADA"));
    for (boolean road : List.of(false, true)) {
      var matrix = road ? roadMatrix : pointMatrix;
      for (String from : matrix.keySet()) {
        for (String to : matrix.keySet()) {
          String owner = account();
          long id = publish(owner, road);
          boolean closed = from.equals(road ? "HABILITADA" : "CERRADO");
          jdbc.update(
              "UPDATE citizen_publication SET operational_status = ?, closed = ? WHERE id = ?",
              from,
              closed,
              id);
          boolean allowed = matrix.get(from).contains(to);
          transition(owner, id, to, "Cambio comprobado", allowed ? 200 : 400);
          assertEquals(allowed ? 1 : 0, historyCount(id));
          assertEquals(
              allowed ? to : from,
              jdbc.queryForObject(
                  "SELECT operational_status FROM citizen_publication WHERE id = ?",
                  String.class,
                  id));
          if (allowed) {
            String snapshot =
                jdbc.queryForObject(
                    "SELECT before_snapshot FROM citizen_publication_change WHERE publication_id = ?",
                    String.class,
                    id);
            assertEquals(from, json.readTree(snapshot).path("operationalStatus").asText());
          }
        }
      }
    }
  }

  @Test
  void everyPrivateRouteRejectsAnonymousOtherAuthorsAndMissingCsrf() throws Exception {
    String owner = account(), other = account();
    long id = publish(owner, false);
    mvc.perform(get("/api/my/publications/{id}/history", id)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/my/publications/{id}/status", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationalStatus\":\"CERRADO\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/my/publications/{id}/status", id)
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationalStatus\":\"CERRADO\"}"))
        .andExpect(status().isForbidden());
    transition(other, id, "CERRADO", "", 404);
    transition(owner, Long.MAX_VALUE, "CERRADO", "", 404);
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", id);
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
        .andExpect(status().isNotFound());
    transition(owner, id, "CERRADO", "", 404);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Oculto")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/my/publications").with(user(owner))).andExpect(jsonPath("$").isEmpty());
    assertEquals(0, historyCount(id));
  }

  @Test
  void historyContainsSnapshotsAndPrivateResponsesAreNotCacheable() throws Exception {
    String owner = account();
    long id = publish(owner, false);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Título corregido")))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", "no-store"));
    String result =
        mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
            .andExpect(status().isOk())
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var change = json.readTree(result).get(0);
    assertEquals(
        "Punto prueba",
        json.readTree(change.path("beforeSnapshot").asText()).path("title").asText());
    assertEquals(
        "Título corregido",
        json.readTree(change.path("afterSnapshot").asText()).path("title").asText());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.authorId").doesNotExist())
        .andExpect(jsonPath("$.beforeSnapshot").doesNotExist())
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
  }

  @Test
  void legacyEnabledRoadIsClosedWithoutDestructiveMigration() throws Exception {
    String owner = account();
    long id = publish(owner, true);
    jdbc.update(
        "UPDATE citizen_publication SET operational_status = 'HABILITADA', closed = FALSE WHERE id = ?",
        id);
    mvc.perform(get("/api/publications/{id}", id)).andExpect(jsonPath("$.closed").value(true));
    mvc.perform(get("/api/publications"))
        .andExpect(jsonPath("$[?(@.id == %s)]", id).doesNotExist());
    transition(owner, id, "BLOQUEADA", "", 400);
    transition(owner, id, "BLOQUEADA", "Nuevo incidente", 200);
    assertEquals(
        "REOPEN",
        jdbc.queryForObject(
            "SELECT action FROM citizen_publication_change WHERE publication_id = ?",
            String.class,
            id));
  }

  @Test
  void auditFailureRollsBackContentAndState() throws Exception {
    String owner = account();
    long id = publish(owner, false);
    // Restricción temporal solo en H2 de pruebas: induce fallo SQL en auditoría sin tocar
    // producción.
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT test_reject_change CHECK (publication_id <> "
            + id
            + ")");
    try {
      org.junit.jupiter.api.Assertions.assertThrows(
          jakarta.servlet.ServletException.class,
          () ->
              mvc.perform(
                  patch("/api/my/publications/{id}", id)
                      .with(user(owner))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(point("No persiste"))));
      org.junit.jupiter.api.Assertions.assertThrows(
          jakarta.servlet.ServletException.class, () -> transition(owner, id, "CERRADO", "", 200));
      assertEquals(
          "Punto prueba",
          jdbc.queryForObject(
              "SELECT title FROM citizen_publication WHERE id = ?", String.class, id));
      assertEquals(
          false,
          jdbc.queryForObject(
              "SELECT closed FROM citizen_publication WHERE id = ?", Boolean.class, id));
      assertEquals(0, historyCount(id));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT test_reject_change");
    }
  }

  @Test
  void concurrentCloseRequestsSerializeAndCreateOnlyOneAuditEntry() throws Exception {
    String owner = account();
    long id = publish(owner, false);
    java.util.concurrent.ExecutorService workers =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Integer> close =
        () -> {
          ready.countDown();
          if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS))
            throw new IllegalStateException("Sin inicio concurrente");
          return mvc.perform(
                  post("/api/my/publications/{id}/status", id)
                      .with(user(owner))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"operationalStatus\":\"CERRADO\"}"))
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var first = workers.submit(close);
      var second = workers.submit(close);
      org.junit.jupiter.api.Assertions.assertTrue(
          ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      var codes =
          new java.util.ArrayList<>(
              List.of(
                  first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
      java.util.Collections.sort(codes);
      assertEquals(List.of(200, 400), codes);
      assertEquals(1, historyCount(id));
    } finally {
      start.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void listOwnUsesOneAuthorFilteredQueryAndPreservesOrderClosedAndPublicMapping() throws Exception {
    String owner = account();
    String other = account();
    List<Long> ids = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      long id = publish(owner, index == 2);
      ids.add(id);
      jdbc.update(
          "UPDATE citizen_publication SET created_at = ? WHERE id = ?",
          Timestamp.from(Instant.parse("2026-10-01T00:00:00Z").plusSeconds(index)),
          id);
    }
    transition(owner, ids.get(1), "CERRADO", "", 200);
    // Fila vial heredada: reutilizar también la interpretación pública de cierre sin migrarla.
    jdbc.update(
        "UPDATE citizen_publication SET operational_status = 'HABILITADA', closed = FALSE WHERE id = ?",
        ids.get(2));
    long hidden = publish(owner, false);
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", hidden);
    publish(other, false);
    jdbc.execute("SET QUERY_STATISTICS TRUE");
    try {
      String body =
          mvc.perform(get("/api/my/publications").with(user(owner)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      var result = json.readTree(body);
      assertEquals(4, result.size());
      for (int index = 0; index < 4; index++) {
        assertEquals(ids.get(3 - index).longValue(), result.get(index).path("id").asLong());
        org.junit.jupiter.api.Assertions.assertFalse(result.get(index).has("authorId"));
        org.junit.jupiter.api.Assertions.assertFalse(result.get(index).has("author_id"));
        org.junit.jupiter.api.Assertions.assertFalse(result.get(index).has("beforeSnapshot"));
        assertEquals("REPORTADA", result.get(index).path("confidenceLevel").asText());
      }
      org.junit.jupiter.api.Assertions.assertTrue(result.get(1).path("closed").asBoolean());
      org.junit.jupiter.api.Assertions.assertTrue(result.get(2).path("closed").asBoolean());
      // H2 cuenta las sentencias ejecutadas, incluidas las consultas globales repetidas.
      long queries =
          jdbc.queryForObject(
              "SELECT COALESCE(SUM(EXECUTION_COUNT), 0) FROM INFORMATION_SCHEMA.QUERY_STATISTICS"
                  + " WHERE LOWER(SQL_STATEMENT) LIKE 'select%citizen_publication%'"
                  + " AND SQL_STATEMENT NOT LIKE '%INFORMATION_SCHEMA%'",
              Long.class);
      assertEquals(
          1, queries, "M publicaciones propias deben costar una consulta, no 1 + M globales");
    } finally {
      // Solo observación de SQL en la H2 de prueba; no dejarla activa en otras suites.
      jdbc.execute("SET QUERY_STATISTICS FALSE");
    }
  }

  private void transition(String userEmail, long id, String state, String reason, int expected)
      throws Exception {
    mvc.perform(
            post("/api/my/publications/{id}/status", id)
                .with(user(userEmail))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new PublicationMaintenanceController.TransitionRequest(state, reason))))
        .andExpect(status().is(expected));
  }

  private long publish(String email, boolean road) throws Exception {
    String body =
        road
            ? """
            {"category":"ROAD","title":"Vía prueba","description":"Daño reciente",
            "location":"Villavicencio","operationalStatus":"BLOQUEADA","impactType":"DERRUMBE",
            "latitude":4.15,"longitude":-73.63}
            """
            : point("Punto prueba");
    String response =
        mvc.perform(
                post("/api/publications")
                    .with(user(email))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).path("id").asLong();
  }

  private static String point(String title) {
    return """
        {"category":"ASSISTANCE","assistanceSubtype":"SHELTER","title":"%s",
        "description":"Apoyo comunitario","location":"Villavicencio",
        "operationalStatus":"DISPONIBLE","latitude":4.15,"longitude":-73.63}
        """
        .formatted(title);
  }

  private String account() {
    String email = "ra04-" + UUID.randomUUID() + "@example.test";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at)"
            + " VALUES (?, ?, ?, ?)",
        email,
        "Cuenta RA04",
        "hash-no-usado",
        Timestamp.from(Instant.now()));
    accounts.add(email);
    return email;
  }

  private int historyCount(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ?",
        Integer.class,
        id);
  }
}

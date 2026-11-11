package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP y SQL: la preconsulta no sustituye al control definitivo; fixtures revierten por
 * transaccion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DuplicatePublicationTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.sql.init.mode", () -> "always");
    String url = System.getenv("RA14_TEST_DB_URL");
    if (url != null
        && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ra14_test_[a-f0-9]{12}"))
      throw new IllegalArgumentException("Base RA14 no aislada");
    p.add(
        "spring.datasource.url",
        () -> url == null ? "jdbc:h2:mem:ra14;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : url);
    p.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("RA14_TEST_DB_USERNAME", "sa"));
    p.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("RA14_TEST_DB_PASSWORD", ""));
  }

  private final java.util.List<String> fixtureEmails = new java.util.ArrayList<>();

  @org.junit.jupiter.api.AfterEach
  void cleanOwnFixtures() {
    for (String email : fixtureEmails) {
      for (String table :
          List.of(
              "publication_duplicate_justification",
              "publication_evidence",
              "citizen_publication_change"))
        jdbc.update(
            "DELETE FROM "
                + table
                + " WHERE publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id=p.author_id WHERE a.email=?)",
            email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email=?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email=?", email);
    }
  }

  String account() {
    String e = "ra14-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account(email,display_name,password_hash,created_at) VALUES (?,'Ficticio','no-login',?)",
        e,
        Timestamp.from(Instant.now()));
    fixtureEmails.add(e);
    return e;
  }

  Map<String, Object> point() {
    return new LinkedHashMap<>(
        Map.of(
            "category",
            "ASSISTANCE",
            "assistanceSubtype",
            "SHELTER",
            "title",
            "Punto ficticio",
            "description",
            "Descripcion ficticia",
            "location",
            "Parque publico",
            "operationalStatus",
            "DISPONIBLE",
            "latitude",
            4.15,
            "longitude",
            -73.63));
  }

  long create(String e, Map<String, Object> body) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/publications")
                        .with(user(e))
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  @Test
  void requiresReasonOnFinalSaveAndKeepsReasonPrivate() throws Exception {
    var e = account();
    var b = point();
    create(e, b);
    mvc.perform(
            post("/api/publications")
                .with(user(e))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(b)))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(e))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(b)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].authorId").doesNotExist());
    b.put("duplicateJustification", "Otro punto de atencion independiente");
    long id = create(e, b);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_duplicate_justification WHERE publication_id=?",
            Integer.class,
            id));
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.duplicateJustification").doesNotExist());
  }

  @Test
  void filtersHiddenOldFutureAndDifferentCategory() throws Exception {
    String e = account();
    var b = point();
    long id = create(e, b);
    for (String sql :
        List.of(
            "UPDATE citizen_publication SET visible=FALSE WHERE id=?",
            "UPDATE citizen_publication SET visible=TRUE,created_at=TIMESTAMP WITH TIME ZONE '2000-01-01 00:00:00Z' WHERE id=?",
            "UPDATE citizen_publication SET created_at=TIMESTAMP WITH TIME ZONE '2099-01-01 00:00:00Z' WHERE id=?",
            "UPDATE citizen_publication SET category='ROAD' WHERE id=?")) {
      jdbc.update(sql, id);
      mvc.perform(
              post("/api/my/publications/possible-duplicates")
                  .with(user(e))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }
  }

  @Test
  void precheckRequiresSessionAndCsrf() throws Exception {
    String body = json.writeValueAsString(point());
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(account()))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    var invalid = point();
    invalid.put("latitude", 91);
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(account()))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
  }

  /** Dos autores distintos compiten por la misma zona: solo uno puede omitir motivo. */
  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentAuthorsMustObserveTheCommittedDuplicate() throws Exception {
    String first = account(), second = account();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var gate = new java.util.concurrent.CountDownLatch(1);
    try {
      var jobs = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (String email : List.of(first, second))
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  return mvc.perform(
                          post("/api/publications")
                              .with(user(email))
                              .with(csrf())
                              .contentType("application/json")
                              .content(json.writeValueAsString(point())))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      gate.countDown();
      var result = new ArrayList<Integer>();
      for (var job : jobs) result.add(job.get(15, java.util.concurrent.TimeUnit.SECONDS));
      result.sort(Integer::compareTo);
      assertEquals(List.of(201, 409), result);
    } finally {
      pool.shutdownNow();
      for (String email : List.of(first, second)) {
        jdbc.update(
            "DELETE FROM citizen_publication_change WHERE publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id=p.author_id WHERE a.email=?)",
            email);
        jdbc.update(
            "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email=?)",
            email);
        jdbc.update("DELETE FROM citizen_account WHERE email=?", email);
      }
    }
  }

  /** La justificacion y las fotos comparten commit o rollback; la guardia se libera al fallar. */
  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void photographsReasonAndRollbackAreAtomic() throws Exception {
    String e = account();
    var body = point();
    long first = create(e, body);
    body.put("duplicateJustification", "Segundo recurso independiente en la misma zona");
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var publication =
        new org.springframework.mock.web.MockMultipartFile(
            "publication", "", "application/json", json.writeValueAsBytes(body));
    var photo =
        new org.springframework.mock.web.MockMultipartFile(
            "files", "prueba.png", "image/png", bytes.toByteArray());
    String created =
        mvc.perform(
                multipart("/api/publications/with-evidence")
                    .file(publication)
                    .file(photo)
                    .with(user(e))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(created).get("id").asLong();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_evidence WHERE publication_id=?", Integer.class, id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_duplicate_justification WHERE publication_id=?",
            Integer.class,
            id));
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class);
    mvc.perform(
            multipart("/api/publications/with-evidence")
                .file(publication)
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "files", "fake.png", "image/png", new byte[] {1, 2}))
                .with(user(e))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(
        before, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class));
    body.put("latitude", 30);
    body.remove("duplicateJustification");
    create(e, body);
  }

  /** Los cerrados geograficos advierten; desaparecidos cerrados u ocultos no se ofrecen. */
  @Test
  void missingPersonRulesAndClosedVisibility() throws Exception {
    String e = account();
    var body = new LinkedHashMap<String, Object>();
    var person =
        new LinkedHashMap<String, Object>(
            Map.of(
                "referenceName",
                "José Maria",
                "ageRange",
                "18–29",
                "physicalDescription",
                "Chaqueta gris",
                "lastSeenPlace",
                "Parque publico",
                "lastSeenAt",
                "2020-01-01T12:00:00Z"));
    body.put("category", "MISSING_PERSON");
    body.put("latitude", 4);
    body.put("longitude", -73);
    body.put("missingPerson", person);
    long id = create(e, body);
    person.put("referenceName", " JOSE, MARIA! ");
    person.put("lastSeenAt", "2021-01-01T12:00:00Z");
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(e))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(jsonPath("$.length()").value(1));
    person.put("referenceName", "Otra referencia");
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(e))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andExpect(jsonPath("$.length()").value(1));
    for (String sql :
        List.of(
            "UPDATE citizen_publication SET closed=TRUE WHERE id=?",
            "UPDATE citizen_publication SET closed=FALSE,visible=FALSE WHERE id=?")) {
      jdbc.update(sql, id);
      mvc.perform(
              post("/api/my/publications/possible-duplicates")
                  .with(user(e))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(body)))
          .andExpect(jsonPath("$").isEmpty());
    }
    var geo = point();
    long geoId = create(e, geo);
    jdbc.update("UPDATE citizen_publication SET closed=TRUE WHERE id=?", geoId);
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .with(user(e))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(geo)))
        .andExpect(jsonPath("$.length()").value(1));
  }

  /** Una transaccion que retiene ASSISTANCE no impide guardar ROAD por otra conexion. */
  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void differentCategoriesUseIndependentLocks() throws Exception {
    String e = account();
    var held = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var blocker =
          pool.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(
                          new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                              jdbc.getDataSource()))
                      .execute(
                          status -> {
                            jdbc.queryForObject(
                                "SELECT category FROM publication_duplicate_guard WHERE category='ASSISTANCE' FOR UPDATE",
                                String.class);
                            held.countDown();
                            try {
                              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("Tiempo excedido");
                            } catch (InterruptedException failure) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(failure);
                            }
                            return true;
                          }));
      assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS));
      var body = point();
      body.put("category", "ROAD");
      body.remove("assistanceSubtype");
      body.put("impactType", "DERRUMBE");
      body.put("operationalStatus", "BLOQUEADA");
      var independent = pool.submit(() -> create(e, body));
      assertTrue(independent.get(5, java.util.concurrent.TimeUnit.SECONDS) >= 1000000);
      release.countDown();
      assertTrue(blocker.get(5, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }
}

package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regresiones HTTP+SQL con reloj controlado y datos exclusivos; no sustituyen aceptación humana.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PublicationConfirmationTests.TimeConfiguration.class)
class PublicationConfirmationTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PublicationConfirmationService confirmations;
  @Autowired private TestClock clock;
  private final List<String> accounts = new ArrayList<>();

  @AfterEach
  void cleanup() {
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM publication_confirmation WHERE confirmer_id IN (SELECT id FROM citizen_account WHERE email = ?) OR publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM publication_confirmation_invalidation WHERE publication_id IN (SELECT p.id FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?)",
          email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
    clock.now = Instant.parse("2026-10-27T12:00:00Z");
  }

  @Test
  void thresholdPrivacyAndRejectedAttemptsDoNotChangeAcceptedHistory() throws Exception {
    String author = account();
    long id = publication(author);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(0))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    submit(id, author, 403);
    String first = account();
    submit(id, first, 200);
    submit(id, first, 409);
    submit(id, account(), 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(2))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    submit(id, account(), 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(3))
        .andExpect(jsonPath("$.confidenceLevel").value("CONFIRMADA"))
        .andExpect(jsonPath("$.authorId").doesNotExist())
        .andExpect(jsonPath("$.confirmerId").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist());
    mvc.perform(get("/api/publications").param("confidenceLevel", "CONFIRMADA"))
        .andExpect(jsonPath("$[?(@.id == %s)]", id).exists());
    assertEquals(3, rows(id));
  }

  @Test
  void securityCsrfHiddenClosedAndExamplesAreRejected() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    mvc.perform(post("/api/publications/{id}/confirmations", id).with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/publications/{id}/confirmations", id).with(user(citizen)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(citizen))
                .with(csrf().useInvalidToken()))
        .andExpect(status().isForbidden());
    submit(1, citizen, 404);
    submit(Long.MAX_VALUE, citizen, 404);
    jdbc.update("UPDATE citizen_publication SET closed = TRUE WHERE id = ?", id);
    submit(id, citizen, 409);
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", id);
    submit(id, citizen, 404);
    mvc.perform(get("/api/publications/{id}", id)).andExpect(status().isNotFound());
    mvc.perform(get("/confirmation.js")).andExpect(status().isOk());
    assertEquals(0, rows(id));
  }

  @Test
  void legacyEnabledRoadIsClosedForPublicQueryAndConfirmation() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    // Fila heredada de RA-03: el estado cierra la vía aunque la bandera antigua sea falsa.
    jdbc.update(
        "UPDATE citizen_publication SET category = 'ROAD', assistance_subtype = NULL,"
            + " operational_status = 'HABILITADA', closed = FALSE WHERE id = ?",
        id);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.closed").value(true));
    var stored =
        jdbc.queryForMap(
            "SELECT category, operational_status FROM citizen_publication WHERE id = ?", id);
    assertEquals("ROAD", stored.get("CATEGORY"));
    assertEquals("HABILITADA", stored.get("OPERATIONAL_STATUS"));
    submit(id, citizen, 409);
    assertEquals(0, rows(id));
  }

  @Test
  void thirtyAcceptedPerSlidingHourAndExactBoundary() throws Exception {
    String author = account(), citizen = account();
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 31; i++) ids.add(publication(author));
    for (int i = 0; i < 30; i++) submit(ids.get(i), citizen, 200);
    submit(ids.get(0), citizen, 409);
    submit(ids.get(30), citizen, 429);
    clock.now = clock.now.plusSeconds(3599);
    submit(ids.get(30), citizen, 429);
    clock.now = clock.now.plusSeconds(1);
    submit(ids.get(30), citizen, 200);
    assertEquals(
        31,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation WHERE confirmer_id = (SELECT id FROM citizen_account WHERE email = ?)",
            Integer.class,
            citizen));
  }

  @Test
  void versionInvalidationRetainsHistoryAllowsReconfirmationAndConsumesQuota() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    submit(id, citizen, 200);
    for (int i = 0; i < 29; i++) {
      confirmations.invalidateForSubstantialChange(id, author, "Descripción o estado modificado");
      submit(id, citizen, 200);
    }
    confirmations.invalidateForSubstantialChange(id, author, "Ubicación modificada");
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(0))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    submit(id, citizen, 429);
    assertEquals(30, rows(id));
    assertEquals(
        30,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation_invalidation WHERE publication_id = ?",
            Integer.class,
            id));
    clock.now = clock.now.plusSeconds(3600);
    submit(id, citizen, 200);
    assertEquals(31, rows(id));
  }

  @Test
  void substantialChangeResetsCommunityThresholdAndInvalidActorOrReasonDoesNotWrite()
      throws Exception {
    String author = account();
    long id = publication(author);
    List<String> citizens = List.of(account(), account(), account());
    for (String citizen : citizens) submit(id, citizen, 200);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> confirmations.invalidateForSubstantialChange(id, citizens.get(0), "No autorizado"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> confirmations.invalidateForSubstantialChange(id, author, " "));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> confirmations.invalidateForSubstantialChange(id, author, "x".repeat(501)));
    confirmations.invalidateForSubstantialChange(id, author, "Estado modificado");
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(0))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    for (String citizen : citizens) submit(id, citizen, 200);
    assertEquals(6, rows(id));
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(3))
        .andExpect(jsonPath("$.confidenceLevel").value("CONFIRMADA"));
  }

  @Test
  void failedConfirmationAndInvalidationRollBackWithoutPartialWrites() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    jdbc.execute(
        "ALTER TABLE publication_confirmation ADD CONSTRAINT ra05_reject_confirmation CHECK (publication_id <> "
            + id
            + ")");
    try {
      assertThrows(jakarta.servlet.ServletException.class, () -> submit(id, citizen, 200));
    } finally {
      jdbc.execute("ALTER TABLE publication_confirmation DROP CONSTRAINT ra05_reject_confirmation");
    }
    assertEquals(0, rows(id));
    submit(id, citizen, 200);
    jdbc.execute(
        "ALTER TABLE publication_confirmation_invalidation ADD CONSTRAINT ra05_reject_invalidation CHECK (publication_id <> "
            + id
            + ")");
    try {
      assertThrows(
          org.springframework.dao.DataIntegrityViolationException.class,
          () -> confirmations.invalidateForSubstantialChange(id, author, "Cambio sustancial"));
    } finally {
      jdbc.execute(
          "ALTER TABLE publication_confirmation_invalidation DROP CONSTRAINT ra05_reject_invalidation");
    }
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationCount").value(1));
  }

  @Test
  void concurrentDuplicateIsAcceptedOnceAndConcurrentQuotaCannotExceedThirty() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    var duplicate = parallel(List.of(() -> code(id, citizen), () -> code(id, citizen)));
    assertEquals(List.of(200, 409), duplicate);
    for (int i = 0; i < 28; i++) submit(publication(author), citizen, 200);
    long first = publication(author), second = publication(author);
    assertEquals(
        List.of(200, 429),
        parallel(List.of(() -> code(first, citizen), () -> code(second, citizen))));
  }

  @Test
  void staleOrMissingVersionAndUnknownAccountsCannotConfirm() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    confirmations.invalidateForSubstantialChange(id, author, "Descripción corregida");
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isConflict());
    for (String body : List.of("{}", "{\"version\":0}")) {
      mvc.perform(
              post("/api/publications/{id}/confirmations", id)
                  .with(user(citizen))
                  .with(csrf())
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    submit(id, "cuenta-inexistente@example.invalid", 401);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            confirmations.invalidateForSubstantialChange(
                id, "ausente@example.invalid", "No permitido"));
    assertEquals(0, rows(id));
  }

  @Test
  void confirmationRacingWithInvalidationCannotValidateUnseenNewVersion() throws Exception {
    String author = account(), citizen = account();
    long id = publication(author);
    List<Integer> results =
        parallel(
            List.of(
                () ->
                    mvc.perform(
                            post("/api/publications/{id}/confirmations", id)
                                .with(user(citizen))
                                .with(csrf())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"version\":1}"))
                        .andReturn()
                        .getResponse()
                        .getStatus(),
                () -> {
                  confirmations.invalidateForSubstantialChange(id, author, "Nuevo estado");
                  return 200;
                }));
    assertTrue(results.equals(List.of(200, 200)) || results.equals(List.of(200, 409)));
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confirmationVersion").value(2))
        .andExpect(jsonPath("$.confirmationCount").value(0));
    assertTrue(rows(id) <= 1);
  }

  private List<Integer> parallel(List<Callable<Integer>> work) throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(work.size());
    CountDownLatch ready = new CountDownLatch(work.size()), start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (Callable<Integer> operation : work)
        futures.add(
            workers.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Sin inicio");
                  return operation.call();
                }));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> results = new ArrayList<>();
      for (Future<Integer> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
      results.sort(Integer::compareTo);
      return results;
    } finally {
      start.countDown();
      workers.shutdownNow();
    }
  }

  private int code(long id, String citizen) throws Exception {
    var versions =
        jdbc.queryForList(
            "SELECT confirmation_version FROM citizen_publication WHERE id = ?", Long.class, id);
    long version = versions.isEmpty() ? 1 : versions.get(0);
    return mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private void submit(long id, String citizen, int expected) throws Exception {
    assertEquals(expected, code(id, citizen));
  }

  private int rows(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM publication_confirmation WHERE publication_id = ?",
        Integer.class,
        id);
  }

  private String account() {
    String email = "ra05-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Prueba RA05', 'hash-no-usado', ?)",
        email,
        Timestamp.from(clock.instant()));
    accounts.add(email);
    return email;
  }

  private long publication(String author) {
    jdbc.update(
        "INSERT INTO citizen_publication (author_id, category, assistance_subtype, title, description, location, operational_status, latitude, longitude, created_at, updated_at) VALUES ((SELECT id FROM citizen_account WHERE email = ?), 'ASSISTANCE', 'SHELTER', 'Punto sintético', 'Descripción', 'Sector público', 'DISPONIBLE', 4.15, -73.63, ?, ?)",
        author,
        Timestamp.from(clock.instant()),
        Timestamp.from(clock.instant()));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM citizen_publication WHERE author_id = (SELECT id FROM citizen_account WHERE email = ?)",
        Long.class,
        author);
  }

  @TestConfiguration
  static class TimeConfiguration {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-27T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}

package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Reglas temporales y HTTP/SQL con reloj controlado; cada fixture se revierte al terminar. */
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:ra06freshness;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(PublicationFreshnessTests.TimeConfig.class)
@Transactional
class PublicationFreshnessTests {
  static final Instant START = Instant.parse("2026-11-01T12:00:00Z");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestClock clock;
  @Autowired PublicationService service;
  @Autowired ModerationActionService actions;
  @Autowired co.redauxilio.identity.OperatorPermissions operators;

  static class TestClock extends Clock {
    Instant now = START;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  String account() {
    String email = "ra06-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Ficticio', 'no-usable', ?)",
        email,
        Timestamp.from(START));
    return email;
  }

  long accountId(String email) {
    return jdbc.queryForObject("SELECT id FROM citizen_account WHERE email=?", Long.class, email);
  }

  long fixture(String category, String author) {
    jdbc.update(
        "INSERT INTO citizen_publication (author_id,category,assistance_subtype,title,description,location,operational_status,latitude,longitude,created_at,updated_at) VALUES (?,?,'SHELTER','Punto prueba','Apoyo comunitario','Villavicencio','DISPONIBLE',4.15,-73.63,?,?)",
        accountId(author),
        category,
        Timestamp.from(START),
        Timestamp.from(START));
    return jdbc.queryForObject(
        "SELECT id FROM citizen_publication WHERE author_id=?", Long.class, accountId(author));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSISTANCE", "ROAD", "HELP_REQUEST", "MISSING_PERSON"})
  void boundariesAndNoSideEffects(String category) throws Exception {
    long id = fixture(category, account());
    Instant expiry = START.plus(Duration.ofHours(category.equals("MISSING_PERSON") ? 72 : 24));
    for (long nanos : new long[] {-1, 0, 1}) {
      clock.now = expiry.plusNanos(nanos);
      mvc.perform(get("/api/publications/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.freshnessStatus").value(nanos > 0 ? "DESACTUALIZADA" : "VIGENTE"))
          .andExpect(jsonPath("$.lastValidActivityAt").value(START.toString()))
          .andExpect(jsonPath("$.freshnessExpiresAt").value(expiry.toString()))
          .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id=?",
            Integer.class,
            id));
    jdbc.update("UPDATE citizen_publication SET closed=TRUE WHERE id=?", id);
    assertTrue(service.findPublication(id).closed());
    assertTrue(
        service.findPublications(category, null, null, null, "DESACTUALIZADA", false, null).stream()
            .noneMatch(p -> p.id() == id));
    jdbc.update("UPDATE citizen_publication SET visible=FALSE WHERE id=?", id);
    mvc.perform(get("/api/publications/{id}", id)).andExpect(status().isNotFound());
  }

  String point(String title) {
    return "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\""
        + title
        + "\",\"description\":\"Apoyo comunitario\",\"location\":\"Villavicencio\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":4.15,\"longitude\":-73.63}";
  }

  @Test
  void identicalSaveAndRealEdit() throws Exception {
    String author = account();
    long id = fixture("ASSISTANCE", author);
    clock.now = START.plus(Duration.ofHours(25));
    for (int i = 0; i < 3; i++)
      jdbc.update(
          "INSERT INTO publication_confirmation (publication_id,publication_version,confirmer_id,confirmed_at) VALUES (?,1,?,?)",
          id,
          accountId(account()),
          Timestamp.from(START.plusSeconds(1800)));
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Punto prueba")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("DESACTUALIZADA"));
    assertEquals(
        START,
        jdbc.queryForObject(
                "SELECT updated_at FROM citizen_publication WHERE id=?", Timestamp.class, id)
            .toInstant());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id=?",
            Integer.class,
            id));
    assertEquals(3, service.findPublication(id).confirmationCount());
    assertEquals(1, service.findPublication(id).confirmationVersion());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point("Punto corregido")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"))
        .andExpect(jsonPath("$.confirmationVersion").value(2));
    assertEquals(0, service.findPublication(id).confirmationCount());
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation WHERE publication_id=?",
            Integer.class,
            id));
    assertEquals(clock.now.toString(), service.findPublication(id).lastValidActivityAt());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation_invalidation WHERE publication_id=?",
            Integer.class,
            id));
  }

  @Test
  void acceptedConfirmationRenewsButRejectedAndOldVersionsDoNot() throws Exception {
    String author = account(), citizen = account();
    long id = fixture("ASSISTANCE", author);
    clock.now = START.plus(Duration.ofHours(25));
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"))
        .andExpect(jsonPath("$.confirmationCount").value(1))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    Instant accepted = clock.now;
    clock.now = accepted.plusSeconds(60);
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(citizen))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isConflict());
    assertEquals(accepted.toString(), service.findPublication(id).lastValidActivityAt());
    jdbc.update("UPDATE citizen_publication SET confirmation_version=2 WHERE id=?", id);
    assertEquals(START.toString(), service.findPublication(id).lastValidActivityAt());
    assertEquals("DESACTUALIZADA", service.findPublication(id).freshnessStatus());
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(account()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/publications/{id}/confirmations", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
        .andExpect(status().isForbidden());
  }

  /** Denuncias, revisión manual y visibilidad no constituyen actividad de vigencia. */
  @Test
  void reviewAndVisibilityPreserveActivity() throws Exception {
    long id = fixture("ASSISTANCE", account());
    clock.now = START.plus(Duration.ofHours(25));
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/publications/{id}/content-reports", id)
                  .with(user(account()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"Información incorrecta\"}"))
          .andExpect(status().isOk());
    assertEquals("EN_REVISION", service.findPublication(id).reviewStatus());
    assertEquals(START.toString(), service.findPublication(id).lastValidActivityAt());
    assertEquals("DESACTUALIZADA", service.findPublication(id).freshnessStatus());
    String operator = account();
    operators.provision(
        accountId(operator),
        operator,
        "GRANT",
        "Fixture",
        "Prueba vigencia",
        UUID.randomUUID().toString());
    for (String action : new String[] {"HIDE", "RESTORE", "REVIEW"}) {
      actions.act(
          id,
          operator,
          new ModerationActionService.ActionRequest(action, "Prueba vigencia", null, null));
      assertEquals(
          START,
          jdbc.queryForObject(
                  "SELECT updated_at FROM citizen_publication WHERE id=?", Timestamp.class, id)
              .toInstant());
    }
    assertEquals("DESACTUALIZADA", service.findPublication(id).freshnessStatus());
  }

  @Test
  void independentCombinedFiltersAndSingleQuery() throws Exception {
    long id = fixture("ASSISTANCE", account());
    clock.now = START.plus(Duration.ofHours(25));
    jdbc.update("UPDATE citizen_publication SET review_status='EN_REVISION' WHERE id=?", id);
    mvc.perform(
            get("/api/publications")
                .param("category", "ASSISTANCE")
                .param("confidenceLevel", "REPORTADA")
                .param("reviewStatus", "EN_REVISION")
                .param("freshnessStatus", " desactualizada "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/freshness.js")).andExpect(status().isOk());
    mvc.perform(get("/api/publications").param("freshnessStatus", "incorrecta"))
        .andExpect(status().isBadRequest());
    assertTrue(service.findPublications(null, null, "DESACTUALIZADA", null, false, null).isEmpty());
    for (int i = 0; i < 12; i++) fixture("ASSISTANCE", account());
    jdbc.execute("SET QUERY_STATISTICS TRUE");
    try {
      service.findPublications(null, null, null, null, false, null);
      long count =
          jdbc.queryForObject(
              "SELECT COALESCE(SUM(EXECUTION_COUNT),0) FROM INFORMATION_SCHEMA.QUERY_STATISTICS WHERE LOWER(SQL_STATEMENT) LIKE 'select%citizen_publication%' AND SQL_STATEMENT NOT LIKE '%INFORMATION_SCHEMA%'",
              Long.class);
      assertEquals(1, count);
    } finally {
      jdbc.execute("SET QUERY_STATISTICS FALSE");
    }
  }
}

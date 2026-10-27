package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprueba persistencia, privacidad, validación y cupo de la publicación con HTTP y SQL reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CitizenPublicationTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  private final List<String> testAccounts = new ArrayList<>();

  @AfterEach
  void removeOwnData() {
    for (String email : testAccounts) {
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
  }

  @Test
  void anonymousAndMissingCsrfCannotPublish() throws Exception {
    String account = createAccount();
    mvc.perform(
            post("/api/publications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/publications")
                .with(user(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(point()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/places").param("q", "Parque central"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/places").with(user(account)).param("q", "a"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void validPointAndRoadAppearInPublicApiWithoutAuthor() throws Exception {
    String account = createAccount();
    JsonNode point = publish(account, point());
    JsonNode road = publish(account, road());
    assertEquals("ASSISTANCE", point.path("category").asText());
    assertEquals("SHELTER", point.path("assistanceSubtype").asText());
    assertEquals("ROAD", road.path("category").asText());
    assertEquals("DERRUMBE", road.path("impactType").asText());
    assertEquals("REPORTADA", point.path("confidenceLevel").asText());
    assertTrue(point.path("id").asLong() >= 1_000_000);
    assertTrue(point.path("authorId").isMissingNode());
    assertTrue(point.path("email").isMissingNode());
    mvc.perform(get("/api/publications/{id}", point.path("id").asLong()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Refugio comunitario"));
    mvc.perform(get("/api/publications").param("category", "ROAD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == %s)]", road.path("id").asLong()).exists());
    jdbc.update(
        "UPDATE citizen_publication SET visible = FALSE WHERE id = ?", point.path("id").asLong());
    mvc.perform(get("/api/publications/{id}", point.path("id").asLong()))
        .andExpect(status().isNotFound());
  }

  @Test
  void invalidInputLeavesNoRowAndSixthSubmissionIsRejected() throws Exception {
    String account = createAccount();
    mvc.perform(
            post("/api/publications")
                .with(user(account))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point().replace("4.15", "91.0")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/publications")
                .with(user(account))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point().replace("SHELTER", "UNKNOWN")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/publications")
                .with(user(account))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(road().replace("Tramo hacia Restrepo", "")))
        .andExpect(status().isBadRequest());
    assertEquals(0L, count(account));
    for (int index = 0; index < 5; index++) publish(account, point());
    mvc.perform(
            post("/api/publications")
                .with(user(account))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point()))
        .andExpect(status().isTooManyRequests());
    assertEquals(5L, count(account));
    assertEquals("ROAD", publish(createAccount(), road()).path("category").asText());
  }

  @Test
  void simultaneousSubmissionsCannotExceedAccountQuota() throws Exception {
    String account = createAccount();
    for (int index = 0; index < 4; index++) publish(account, point());
    CountDownLatch start = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(2);
    try {
      var first = workers.submit(() -> submitTogether(account, start));
      var second = workers.submit(() -> submitTogether(account, start));
      start.countDown();
      int firstStatus = first.get(20, TimeUnit.SECONDS);
      int secondStatus = second.get(20, TimeUnit.SECONDS);
      assertEquals(1, (firstStatus == 201 ? 1 : 0) + (secondStatus == 201 ? 1 : 0));
      assertEquals(1, (firstStatus == 429 ? 1 : 0) + (secondStatus == 429 ? 1 : 0));
      assertEquals(5L, count(account));
    } finally {
      workers.shutdownNow();
    }
  }

  private int submitTogether(String account, CountDownLatch start) throws Exception {
    start.await();
    return mvc.perform(
            post("/api/publications")
                .with(user(account))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(point()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private JsonNode publish(String account, String body) throws Exception {
    String response =
        mvc.perform(
                post("/api/publications")
                    .with(user(account))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response);
  }

  private String createAccount() {
    String email = "ra03-" + UUID.randomUUID() + "@example.test";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, ?, ?, ?)",
        email,
        "Cuenta de prueba",
        "hash-no-usado",
        Timestamp.from(Instant.now()));
    testAccounts.add(email);
    return email;
  }

  private long count(String email) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE a.email = ?",
        Long.class,
        email);
  }

  private static String point() {
    return """
        {"category":"ASSISTANCE","assistanceSubtype":"SHELTER","title":"Refugio comunitario",
        "description":"Espacio de apoyo","location":"Villavicencio","operationalStatus":"DISPONIBLE",
        "latitude":4.15,"longitude":-73.63}
        """;
  }

  private static String road() {
    return """
        {"category":"ROAD","title":"Tramo hacia Restrepo","description":"Paso reducido",
        "location":"Villavicencio","operationalStatus":"PARCIALMENTE HABILITADA",
        "impactType":"DERRUMBE","latitude":4.16,"longitude":-73.64}
        """;
  }
}

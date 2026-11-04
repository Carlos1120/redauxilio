package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Comprueba el historial HTTP/SQL en H2 o PostgreSQL aislado con casos y cuentas ficticios. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PublicationFreshnessTests.TimeConfig.class)
class MissingPersonSightingTests {
  private static final Instant START = Instant.parse("2026-11-03T12:00:00Z");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PublicationFreshnessTests.TestClock clock;
  String author;
  String observer;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry properties) {
    properties.add("spring.sql.init.mode", () -> "always");
    properties.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "RA10_TEST_DB_URL",
                    "jdbc:h2:mem:ra10sighting;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("RA10_TEST_DB_USERNAME", "sa"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("RA10_TEST_DB_PASSWORD", ""));
  }

  @BeforeEach
  void resetData() {
    for (String table :
        List.of(
            "missing_person_sighting_evidence",
            "missing_person_sighting",
            "publication_authorized_contact",
            "publication_moderation_action",
            "publication_confirmation_invalidation",
            "publication_confirmation",
            "publication_content_report",
            "publication_evidence",
            "citizen_publication_change",
            "citizen_publication",
            "operator_permission_audit",
            "operator_permission",
            "citizen_account")) jdbc.update("DELETE FROM " + table);
    clock.now = START;
    author = account("Autor del caso");
    observer = account("Observador ciudadano");
  }

  @Test
  void recordsPhotoAndHistoryWithoutChangingCaseStateOrExposingAuthor() throws Exception {
    long caseId = createCase();
    String initialUpdatedAt =
        jdbc.queryForObject(
            "SELECT CAST(updated_at AS VARCHAR) FROM citizen_publication WHERE id = ?",
            String.class,
            caseId);
    MockMultipartFile photo = png();
    mvc.perform(upload(caseId, sightingRequest(), photo).with(user(observer)).with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.observedAt").value("2026-11-03T11:30:00Z"))
        .andExpect(jsonPath("$.location").value("Parque público ficticio"))
        .andExpect(jsonPath("$.latitude").value(4.151))
        .andExpect(jsonPath("$.evidence[0].contentType").value("image/png"));

    String history =
        mvc.perform(get("/api/publications/{id}/sightings", caseId))
            .andExpect(status().isOk())
            .andExpect(
                header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$[0].description").value("Observación ficticia del caso"))
            .andExpect(jsonPath("$[0].authorId").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(history.contains("@example.invalid"));
    JsonNode evidence = json.readTree(history).get(0).get("evidence").get(0);
    long sightingId = json.readTree(history).get(0).get("id").asLong();
    long evidenceId = evidence.get("id").asLong();
    String evidenceUrl = evidence.get("url").asText();
    mvc.perform(get(evidenceUrl))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Type", "image/png"));
    assertEquals(
        "DESAPARECIDA",
        jdbc.queryForObject(
            "SELECT operational_status FROM citizen_publication WHERE id = ?",
            String.class,
            caseId));
    assertEquals(
        initialUpdatedAt,
        jdbc.queryForObject(
            "SELECT CAST(updated_at AS VARCHAR) FROM citizen_publication WHERE id = ?",
            String.class,
            caseId));
    assertEquals(
        jdbc.queryForObject("SELECT id FROM citizen_account WHERE email = ?", Long.class, observer),
        jdbc.queryForObject(
            "SELECT author_id FROM missing_person_sighting WHERE id = ?", Long.class, sightingId));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM missing_person_sighting_evidence WHERE id = ? AND sighting_id = ?",
            Integer.class,
            evidenceId,
            sightingId));
  }

  @Test
  void rejectsInvalidPhotoAndInvalidStateWithoutPartialWrites() throws Exception {
    long caseId = createCase();
    mvc.perform(
            upload(
                    caseId,
                    sightingRequest(),
                    new MockMultipartFile(
                        "files", "fake.png", "image/png", "not an image".getBytes()))
                .with(user(observer))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(0, sightingCount(caseId));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM missing_person_sighting_evidence", Integer.class));

    jdbc.update(
        "UPDATE citizen_publication SET operational_status='ENCONTRADA' WHERE id=?", caseId);
    mvc.perform(upload(caseId, sightingRequest()).with(user(observer)).with(csrf()))
        .andExpect(status().isConflict());
    assertEquals(0, sightingCount(caseId));
  }

  @Test
  void rollsBackSightingWhenImageStorageFailsAfterInsert() throws Exception {
    long caseId = createCase();
    jdbc.execute(
        "ALTER TABLE missing_person_sighting_evidence ADD CONSTRAINT ra10_test_reject_image CHECK (OCTET_LENGTH(image_bytes) > 1000)");
    try {
      assertThrows(
          ServletException.class,
          () ->
              mvc.perform(
                  upload(caseId, sightingRequest(), png()).with(user(observer)).with(csrf())));
      assertEquals(0, sightingCount(caseId));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM missing_person_sighting_evidence", Integer.class));
    } finally {
      jdbc.execute(
          "ALTER TABLE missing_person_sighting_evidence DROP CONSTRAINT ra10_test_reject_image");
    }
  }

  @Test
  void requiresAuthenticationAndCsrfAndHidesHiddenCases() throws Exception {
    long caseId = createCase();
    mvc.perform(upload(caseId, sightingRequest()).with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(upload(caseId, sightingRequest()).with(user(observer)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/publications/{id}/sightings", caseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    jdbc.update("UPDATE citizen_publication SET visible=FALSE WHERE id=?", caseId);
    mvc.perform(get("/api/publications/{id}/sightings", caseId)).andExpect(status().isNotFound());
    mvc.perform(upload(caseId, sightingRequest()).with(user(observer)).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(0, sightingCount(caseId));
  }

  private MockMultipartHttpServletRequestBuilder upload(
      long id, String request, MockMultipartFile... files) throws Exception {
    MockMultipartHttpServletRequestBuilder builder =
        multipart("/api/my/publications/{id}/sightings", id)
            .file(
                new MockMultipartFile(
                    "sighting", "", MediaType.APPLICATION_JSON_VALUE, request.getBytes()));
    for (MockMultipartFile file : files) builder.file(file);
    return builder;
  }

  private long createCase() throws Exception {
    String response =
        mvc.perform(
                post("/api/publications")
                    .with(user(author))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(publicationRequest())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asLong();
  }

  private Map<String, Object> publicationRequest() {
    var missing = new LinkedHashMap<String, Object>();
    missing.put("referenceName", "Caso ficticio");
    missing.put("ageRange", "18–29");
    missing.put("physicalDescription", "Chaqueta gris");
    missing.put("lastSeenPlace", "Parque ficticio");
    missing.put("lastSeenAt", START.minusSeconds(3600).toString());
    return Map.of(
        "category",
        "MISSING_PERSON",
        "latitude",
        4.15,
        "longitude",
        -73.63,
        "missingPerson",
        missing);
  }

  private static String sightingRequest() {
    return "{\"observedAt\":\"2026-11-03T11:30:00Z\",\"location\":\"Parque público ficticio\",\"latitude\":4.1514,\"longitude\":-73.6295,\"description\":\"Observación ficticia del caso\"}";
  }

  private int sightingCount(long publicationId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM missing_person_sighting WHERE publication_id = ?",
        Integer.class,
        publicationId);
  }

  private String account(String name) {
    String email = "ra10-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account(email, display_name, password_hash, created_at) VALUES (?, ?, 'no-login', ?)",
        email,
        name,
        Timestamp.from(START));
    return email;
  }

  private static MockMultipartFile png() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0x336699);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return new MockMultipartFile("files", "sighting.png", "image/png", bytes.toByteArray());
  }
}

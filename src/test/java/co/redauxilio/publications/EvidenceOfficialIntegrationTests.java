package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integra fotografías y verificación sobre el mismo caso; H2 o PostgreSQL aislado RA13_TEST_DB_*.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvidenceOfficialIntegrationTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    OfficialVerificationTests.testDatabase(properties);
    if (System.getenv("RA13_TEST_DB_URL") == null || System.getenv("RA13_TEST_DB_URL").isBlank()) {
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:evidenceOfficialIntegration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired OfficialVerificationService verifications;
  private final List<String> accounts = new ArrayList<>();
  private long publicationId;

  /** Retira únicamente las fixtures propias para no contaminar las pruebas de fallo inducido. */
  @AfterEach
  void cleanFixtures() {
    jdbc.update(
        "DELETE FROM official_verification_action WHERE verification_id IN (SELECT id FROM official_verification WHERE publication_id=?)",
        publicationId);
    for (String table :
        List.of(
            "official_verification",
            "publication_confirmation_invalidation",
            "citizen_publication_change",
            "publication_evidence")) {
      jdbc.update("DELETE FROM " + table + " WHERE publication_id=?", publicationId);
    }
    jdbc.update("DELETE FROM citizen_publication WHERE id=?", publicationId);
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM verification_permission_audit WHERE account_id IN (SELECT id FROM citizen_account WHERE email=?) OR actor_id IN (SELECT id FROM citizen_account WHERE email=?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM verification_permission WHERE account_id IN (SELECT id FROM citizen_account WHERE email=?) OR granted_by IN (SELECT id FROM citizen_account WHERE email=?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM operator_permission WHERE account_id IN (SELECT id FROM citizen_account WHERE email=?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email=?", email);
    }
  }

  /** La edición invalida el respaldo oficial, pero conserva foto, autorización e historia. */
  @Test
  void multipartVerificationAndEditionPreserveEvidenceAndHistory() throws Exception {
    String author = account(), verifier = account(), operator = account();
    jdbc.update(
        "INSERT INTO operator_permission(account_id,role,granted_at) SELECT id,'ROLE_OPERATOR',? FROM citizen_account WHERE email=?",
        Timestamp.from(Instant.now()),
        operator);
    verifications.changePermission(
        verifier, operator, "GRANT", "Autorización ficticia de integración");
    String body =
        "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Caso de integración\",\"description\":\"Descripción ficticia\",\"location\":\"Sector público\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":4.15,\"longitude\":-73.63}";
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    String created =
        mvc.perform(
                multipart("/api/publications/with-evidence")
                    .file(
                        new MockMultipartFile(
                            "publication",
                            "",
                            "application/json",
                            body.getBytes(StandardCharsets.UTF_8)))
                    .file(
                        new MockMultipartFile(
                            "files", "ficticia.png", "image/png", bytes.toByteArray()))
                    .with(user(author))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(created).path("id").asLong();
    publicationId = id;
    String images =
        mvc.perform(get("/api/publications/{id}/evidence", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String imageUrl = json.readTree(images).get(0).path("url").asText();
    mvc.perform(get(imageUrl))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", id)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Inspección ficticia\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confidenceLevel").value("VERIFICADA"));
    mvc.perform(get("/api/publications/{id}/official-verifications", id))
        .andExpect(jsonPath("$.length()").value(1));
    verifications.changePermission(verifier, operator, "REVOKE", "Retiro ficticio de permiso");
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", id)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Nuevo intento\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType("application/json")
                .content(body.replace("Descripción ficticia", "Descripción rectificada")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    mvc.perform(get("/api/publications/{id}/official-verifications", id))
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(get(imageUrl))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    assertEquals(
        2L,
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id=?", Long.class, id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM official_verification WHERE publication_id=? AND publication_version=1",
            Integer.class,
            id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM official_verification_action a JOIN official_verification v ON v.id=a.verification_id WHERE v.publication_id=? AND a.action='VERIFY'",
            Integer.class,
            id));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id=? AND action='EDIT'",
            Integer.class,
            id));
  }

  private String account() {
    String email = "integration-" + UUID.randomUUID() + "@example.invalid";
    accounts.add(email);
    jdbc.update(
        "INSERT INTO citizen_account(email,display_name,password_hash,created_at) VALUES (?,'Cuenta ficticia','no-login',?)",
        email,
        Timestamp.from(Instant.now()));
    return email;
  }
}

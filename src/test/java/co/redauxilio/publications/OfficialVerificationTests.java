package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba permisos, versiones, privacidad y auditoría oficial mediante HTTP y SQL. */
@SpringBootTest
@AutoConfigureMockMvc
class OfficialVerificationTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OfficialVerificationService verifications;
  @Autowired private PublicationConfirmationService confirmations;
  private final List<String> accounts = new ArrayList<>();
  private final List<Long> publications = new ArrayList<>();

  @AfterEach
  void clean() {
    for (long id : publications) {
      jdbc.update(
          "DELETE FROM official_verification_action WHERE verification_id IN (SELECT id FROM official_verification WHERE publication_id = ?)",
          id);
      jdbc.update("DELETE FROM official_verification WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM publication_confirmation WHERE publication_id = ?", id);
      jdbc.update("DELETE FROM publication_confirmation_invalidation WHERE publication_id = ?", id);
    }
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM verification_permission_audit WHERE account_id IN (SELECT id FROM citizen_account WHERE email = ?) OR actor_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM verification_permission WHERE account_id IN (SELECT id FROM citizen_account WHERE email = ?) OR granted_by IN (SELECT id FROM citizen_account WHERE email = ?)",
          email,
          email);
      jdbc.update(
          "DELETE FROM operator_permission_audit WHERE account_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update(
          "DELETE FROM operator_permission WHERE account_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN (SELECT id FROM citizen_account WHERE email = ?)",
          email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
  }

  @Test
  void onlyAuthorizedAccountsCanVerifyAndPublicOutputContainsEvidenceWithoutEmail()
      throws Exception {
    String author = account("Autor ciudadano");
    String verifier = account("Entidad autorizada");
    String operator = operator();
    long publication = publication(author);
    for (int index = 0; index < 3; index++)
      jdbc.update(
          "INSERT INTO publication_confirmation (publication_id, publication_version, confirmer_id, confirmed_at) VALUES (?, 1, (SELECT id FROM citizen_account WHERE email = ?), ?)",
          publication,
          account("Confirmador comunitario " + index),
          Timestamp.from(Instant.now()));
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", publication)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Inspección en sitio\",\"reference\":\"ACTA-42\"}"))
        .andExpect(status().isForbidden());
    assertEquals(0, count("official_verification", publication));

    mvc.perform(
            post("/api/operator/verifier-permissions")
                .with(user(author))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + verifier
                        + "\",\"action\":\"GRANT\",\"reason\":\"Acreditación comprobada\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/operator/verifier-permissions")
                .with(user(operator).roles("OPERATOR"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + verifier
                        + "\",\"action\":\"GRANT\",\"reason\":\"Acreditación comprobada\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granted").value(true));
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", publication)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Inspección en sitio\",\"reference\":\"ACTA-42\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/publications/{id}", publication))
        .andExpect(jsonPath("$.confidenceLevel").value("VERIFICADA"))
        .andExpect(jsonPath("$.confirmationCount").value(3))
        .andExpect(jsonPath("$.email").doesNotExist());
    mvc.perform(get("/api/publications/{id}/official-verifications", publication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].accountName").value("Entidad autorizada"))
        .andExpect(jsonPath("$[0].note").value("Inspección en sitio"))
        .andExpect(jsonPath("$[0].reference").value("ACTA-42"))
        .andExpect(jsonPath("$[0].email").doesNotExist());
  }

  @Test
  void permissionRevocationAndVersionChangeKeepOldVerificationAsHistory() throws Exception {
    String author = account("Autor de prueba");
    String verifier = account("Verificador de prueba");
    String operator = operator();
    long publication = publication(author);
    verifications.changePermission(verifier, operator, "GRANT", "Acreditación vigente");
    long verification = verifications.verify(publication, verifier, "Revisión institucional", null);

    mvc.perform(
            post(
                    "/api/operator/publications/{id}/official-verifications/{verificationId}/revoke",
                    publication,
                    verification)
                .with(user(operator).roles("OPERATOR"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Acta corregida\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/publications/{id}", publication))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"))
        .andExpect(jsonPath("$.confirmationCount").value(0));
    mvc.perform(
            get("/api/operator/publications/{id}/official-verifications", publication)
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].revoked").value(true))
        .andExpect(jsonPath("$[0].revokedBy").value("Operador autorizado"))
        .andExpect(jsonPath("$[0].revokeReason").value("Acta corregida"))
        .andExpect(jsonPath("$[0].note").value("Revisión institucional"));

    mvc.perform(
            post("/api/operator/verifier-permissions")
                .with(user(operator).roles("OPERATOR"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + verifier
                        + "\",\"action\":\"REVOKE\",\"reason\":\"Acreditación vencida\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granted").value(false));
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", publication)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Nueva revisión\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/operator/verifier-permissions/history")
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].action").value("REVOKE"))
        .andExpect(jsonPath("$[1].action").value("GRANT"));

    long activeVerification =
        verifications.verifyAsOperator(
            publication,
            operator,
            "Corrección operativa",
            "CASO-19",
            "Se corrigió el acta oficial");
    confirmations.invalidateForSubstantialChange(publication, author, "Ubicación corregida");
    mvc.perform(get("/api/publications/{id}", publication))
        .andExpect(jsonPath("$.confidenceLevel").value("REPORTADA"));
    mvc.perform(get("/api/publications/{id}/official-verifications", publication))
        .andExpect(jsonPath("$").isEmpty());
    assertEquals(2, count("official_verification", publication));
    assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM official_verification_action WHERE verification_id IN (SELECT id FROM official_verification WHERE publication_id = ?)",
            Integer.class,
            publication));
    assertEquals(1, activeVerification > 0 ? 1 : 0);
  }

  @Test
  void csrfAndMissingBothNoteAndReferenceCannotWrite() throws Exception {
    String author = account("Autora");
    String verifier = account("Verificador");
    String operator = operator();
    long publication = publication(author);
    verifications.changePermission(verifier, operator, "GRANT", "Verificado");
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", publication)
                .with(user(verifier))
                .contentType("application/json")
                .content("{\"note\":\"\",\"reference\":\"\"}"))
        .andExpect(status().isForbidden());
    assertEquals(0, count("official_verification", publication));
    mvc.perform(
            post("/api/my/publications/{id}/official-verifications", publication)
                .with(user(verifier))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"\",\"reference\":\"\"}"))
        .andExpect(status().isBadRequest());
    assertEquals(0, count("official_verification", publication));
  }

  private int count(String table, long publication) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE publication_id = ?", Integer.class, publication);
  }

  private String account(String displayName) {
    String email = "ra13-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, ?, 'hash-no-usado', ?)",
        email,
        displayName,
        Timestamp.from(Instant.now()));
    accounts.add(email);
    return email;
  }

  private String operator() {
    String email = account("Operador autorizado");
    jdbc.update(
        "INSERT INTO operator_permission (account_id, role, granted_at) SELECT id, 'ROLE_OPERATOR', ? FROM citizen_account WHERE email = ?",
        Timestamp.from(Instant.now()),
        email);
    return email;
  }

  private long publication(String author) {
    jdbc.update(
        "INSERT INTO citizen_publication (author_id, category, assistance_subtype, title, description, location, operational_status, latitude, longitude, created_at, updated_at) VALUES ((SELECT id FROM citizen_account WHERE email = ?), 'ASSISTANCE', 'SHELTER', 'Punto de prueba', 'Descripción', 'Sector público', 'DISPONIBLE', 4.15, -73.63, ?, ?)",
        author,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    long id =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM citizen_publication WHERE author_id = (SELECT id FROM citizen_account WHERE email = ?)",
            Long.class,
            author);
    publications.add(id);
    return id;
  }
}

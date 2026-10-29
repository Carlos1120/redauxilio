package co.redauxilio.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Roles reales, concurrencia y rollback en H2 aislada; no aprovisiona cuentas ni permisos del
 * usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperatorSecurityTests {
  @Autowired private OperatorPermissions operators;
  @Autowired private CitizenUserDetailsService details;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private PasswordEncoder encoder;
  private final List<String> emails = new ArrayList<>();
  private static final String PASSWORD = "ClaveFicticiaRA08!";

  @AfterEach
  void cleanup() {
    for (String email : emails) {
      long id = id(email);
      jdbc.update("DELETE FROM operator_permission_audit WHERE account_id = ?", id);
      jdbc.update("DELETE FROM operator_permission WHERE account_id = ?", id);
      jdbc.update("DELETE FROM citizen_account WHERE id = ?", id);
    }
  }

  @Test
  void registrationCannotAssignOperatorAndNoPublicProvisioningExists() throws Exception {
    String email = "ra08-" + UUID.randomUUID() + "@example.invalid";
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("email", email)
                .param("displayName", "Prueba")
                .param("password", PASSWORD)
                .param("role", "ROLE_OPERATOR")
                .param("roles", "OPERATOR")
                .param("operator", "true"))
        .andExpect(status().is3xxRedirection());
    emails.add(email);
    assertFalse(operators.isOperator(email));
    assertTrue(
        details.loadUserByUsername(email).getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("ROLE_OPERATOR")));
    mvc.perform(get("/api/operator/publications").with(user(email).roles("OPERATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/operator/provision").with(user(email)).with(csrf()))
        .andExpect(status().isForbidden());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM operator_permission WHERE account_id = ?",
            Integer.class,
            id(email)));
  }

  @Test
  void grantNeedsNewLoginAndRevokeAppliesToExistingSessionWithoutReplayingOldGrant()
      throws Exception {
    String email = account();
    jdbc.update(
        "UPDATE citizen_account SET password_hash = ? WHERE email = ?",
        encoder.encode(PASSWORD),
        email);
    MockHttpSession oldSession = login(email);
    mvc.perform(get("/api/operator/publications").session(oldSession))
        .andExpect(status().isForbidden());
    String key = UUID.randomUUID().toString();
    provision(email, "GRANT", "Alta controlada", key);
    mvc.perform(get("/api/operator/publications").session(oldSession))
        .andExpect(status().isForbidden());
    MockHttpSession session = login(email);
    mvc.perform(get("/api/operator/publications").session(session)).andExpect(status().isOk());
    provision(email, "REVOKE", "Retiro controlado", UUID.randomUUID().toString());
    mvc.perform(get("/api/operator/publications").session(session))
        .andExpect(status().isForbidden());
    provision(email, "GRANT", "Alta controlada", key);
    assertFalse(operators.isOperator(email));
    assertEquals(2, audit(email));
    var row = jdbc.queryForMap("SELECT * FROM operator_permission_audit WHERE request_id = ?", key);
    assertEquals("Equipo de prueba", row.get("actor"));
    assertEquals(false, row.get("before_granted"));
    assertEquals(true, row.get("after_granted"));
    assertNotNull(row.get("changed_at"));
  }

  @Test
  void explicitProvisionerIsDisabledByDefaultAndIdempotentWhenConfigured() {
    String email = account(), key = UUID.randomUUID().toString();
    new OperatorProvisioner(operators, "", 0, "", "", "", "")
        .run(new DefaultApplicationArguments());
    assertFalse(operators.isOperator(email));
    var runner =
        new OperatorProvisioner(
            operators, "GRANT", id(email), email, "Equipo de prueba", "Inicio controlado", key);
    runner.run(new DefaultApplicationArguments());
    runner.run(new DefaultApplicationArguments());
    assertTrue(operators.isOperator(email));
    assertEquals(1, audit(email));
    assertThrows(
        IllegalArgumentException.class,
        () -> provision(email, "REVOKE", "Cambio con UUID reutilizado", key));
    assertEquals(1, audit(email));
  }

  @Test
  void invalidOrMismatchedProvisioningNeverGrantsPrivileges() {
    String email = account();
    long accountId = id(email);
    for (String action : List.of("", "PUBLIC", "GRANT_LONG"))
      assertThrows(
          IllegalArgumentException.class,
          () ->
              operators.provision(
                  accountId, email, action, "Prueba", "Motivo", UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            operators.provision(
                0, email, "GRANT", "Prueba", "Motivo", UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            operators.provision(
                Long.MAX_VALUE, email, "GRANT", "Prueba", "Motivo", UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            operators.provision(
                accountId,
                "otra@example.invalid",
                "GRANT",
                "Prueba",
                "Motivo",
                UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            operators.provision(
                accountId, email, "GRANT", null, "Motivo", UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            operators.provision(
                accountId,
                email,
                "GRANT",
                "Prueba",
                "x".repeat(501),
                UUID.randomUUID().toString()));
    assertThrows(
        IllegalArgumentException.class, () -> provision(email, "GRANT", "Motivo", "no-uuid"));
    assertFalse(operators.isOperator(email));
    assertEquals(0, audit(email));
  }

  @Test
  void grantAndRevokeRollbackIfTheirAuditCannotBeWritten() {
    String email = account();
    jdbc.execute(
        "ALTER TABLE operator_permission_audit ADD CONSTRAINT ra08_audit_failure CHECK (reason <> 'FALLO')");
    try {
      assertThrows(
          DataIntegrityViolationException.class,
          () -> provision(email, "GRANT", "FALLO", UUID.randomUUID().toString()));
      assertFalse(operators.isOperator(email));
      assertEquals(0, audit(email));
      provision(email, "GRANT", "Alta válida", UUID.randomUUID().toString());
      assertThrows(
          DataIntegrityViolationException.class,
          () -> provision(email, "REVOKE", "FALLO", UUID.randomUUID().toString()));
      assertTrue(operators.isOperator(email));
      assertEquals(1, audit(email));
    } finally {
      jdbc.execute("ALTER TABLE operator_permission_audit DROP CONSTRAINT ra08_audit_failure");
    }
  }

  @Test
  void concurrentProvisioningOfSameRequestCreatesOnePermissionAndOneImmutableEvent()
      throws Exception {
    String email = account(), key = UUID.randomUUID().toString();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Void> task =
        () -> {
          start.await();
          provision(email, "GRANT", "Concurrente", key);
          return null;
        };
    try {
      Future<Void> first = pool.submit(task), second = pool.submit(task);
      start.countDown();
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
      assertTrue(operators.isOperator(email));
      assertEquals(1, audit(email));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM operator_permission WHERE account_id = ?",
              Integer.class,
              id(email)));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void temporaryLockAndMissingOrAnonymousIdentityDenyOperatorAccess() {
    String email = account();
    provision(email, "GRANT", "Alta", UUID.randomUUID().toString());
    assertTrue(operators.isOperator(" " + email.toUpperCase(java.util.Locale.ROOT) + " "));
    jdbc.update(
        "UPDATE citizen_account SET locked_until = ? WHERE email = ?",
        Timestamp.from(Instant.now().plusSeconds(900)),
        email);
    assertFalse(operators.isOperator(email));
    assertFalse(details.loadUserByUsername(email).isAccountNonLocked());
    assertFalse(operators.isOperator(null));
    assertFalse(operators.isOperator(" "));
    assertFalse(operators.isOperator("ausente@example.invalid"));
    assertFalse(operators.allows(null));
    assertFalse(
        operators.allows(
            new AnonymousAuthenticationToken(
                "test", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))));
  }

  private String account() {
    String email = "ra08-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Prueba RA08', 'sin-credencial-usable', ?)",
        email,
        Timestamp.from(Instant.now()));
    emails.add(email);
    return email;
  }

  private long id(String email) {
    return jdbc.queryForObject("SELECT id FROM citizen_account WHERE email = ?", Long.class, email);
  }

  private int audit(String email) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM operator_permission_audit WHERE account_id = ?",
        Integer.class,
        id(email));
  }

  private void provision(String email, String action, String reason, String key) {
    operators.provision(id(email), email, action, "Equipo de prueba", reason, key);
  }

  private MockHttpSession login(String email) throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/login").with(csrf()).param("username", email).param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getRequest()
            .getSession(false);
  }
}

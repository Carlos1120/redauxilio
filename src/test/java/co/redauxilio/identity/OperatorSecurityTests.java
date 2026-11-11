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
 * Permisos y restricciones en H2 o PostgreSQL desechable; limpia únicamente sus cuentas ficticias.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperatorSecurityTests {
  /** Solo admite PostgreSQL local con nombre de base exclusivo de pruebas RA-16. */
  @org.springframework.test.context.DynamicPropertySource
  static void testDatabase(org.springframework.test.context.DynamicPropertyRegistry properties) {
    String url = System.getenv("RA16_TEST_DB_URL");
    if (url != null
        && !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ra16_test_[a-f0-9]{12}"))
      throw new IllegalArgumentException("Base RA16 no aislada");
    properties.add("spring.sql.init.mode", () -> "always");
    properties.add(
        "spring.datasource.url",
        () -> url == null ? "jdbc:h2:mem:ra16;MODE=PostgreSQL;DB_CLOSE_DELAY=-1" : url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("RA16_TEST_DB_USERNAME", "sa"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("RA16_TEST_DB_PASSWORD", ""));
  }

  @Autowired private OperatorPermissions operators;
  @Autowired private AccountRestrictionService restrictions;
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
      jdbc.update(
          "DELETE FROM account_restriction_audit WHERE account_id = ? OR actor_id = ?", id, id);
      jdbc.update("DELETE FROM account_restriction WHERE account_id = ? OR blocked_by = ?", id, id);
      jdbc.update(
          "DELETE FROM verification_permission_audit WHERE account_id = ? OR actor_id = ?", id, id);
      jdbc.update(
          "DELETE FROM verification_permission WHERE account_id = ? OR granted_by = ?", id, id);
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

  @Test
  void blockAndReactivationAreAuditedAndAffectExistingSessionsWithoutHidingPublicReads()
      throws Exception {
    String operator = account();
    String citizen = account();
    provision(operator, "GRANT", "Operador de prueba", UUID.randomUUID().toString());
    provision(
        citizen, "GRANT", "Permiso previo a la persona objetivo", UUID.randomUUID().toString());
    jdbc.update(
        "INSERT INTO verification_permission (account_id, granted_by, granted_at) VALUES (?, ?, ?)",
        id(citizen),
        id(operator),
        Timestamp.from(Instant.now()));
    jdbc.update(
        "UPDATE citizen_account SET password_hash = ? WHERE email = ?",
        encoder.encode(PASSWORD),
        citizen);
    MockHttpSession existingSession = login(citizen);
    var actor = user(operator).roles("OPERATOR");

    mvc.perform(get("/operator").with(actor))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("Bloquear o reactivar una cuenta")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("account-restrictions.js")));

    mvc.perform(
            get("/api/operator/accounts/restriction").param("email", citizen).with(user(citizen)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + citizen
                        + "\",\"action\":\"BLOCK\",\"reason\":\"Abuso confirmado\"}"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(jsonPath("$.blocked").value(true))
        .andExpect(jsonPath("$.currentReason").value("Abuso confirmado"))
        .andExpect(jsonPath("$.history[0].actorId").value(id(operator)))
        .andExpect(jsonPath("$.history[0].actorDisplayName").value("Prueba RA08"))
        .andExpect(jsonPath("$.history[0].afterBlocked").value(true));

    mvc.perform(get("/api/publications").session(existingSession)).andExpect(status().isOk());
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .session(existingSession)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/operator/publications").with(user(citizen).roles("OPERATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/my/verification-permission").with(user(citizen)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canVerify").value(false));
    mvc.perform(
            post("/api/publications")
                .with(user(citizen))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    // Incluye las rutas nuevas de RA-14 y la variante multipart de creación.
    for (String path :
        List.of(
            "/api/my/publications/possible-duplicates", "/api/my/publications/1000000/sightings")) {
      mvc.perform(
              post(path)
                  .with(user(citizen))
                  .with(csrf())
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(multipart("/api/publications").with(user(citizen)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/publications/1000000/confirmations")
                .with(user(citizen))
                .with(csrf())
                .contentType("application/json")
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/publications/1000000/content-reports")
                .with(user(citizen))
                .with(csrf())
                .contentType("application/json")
                .content("{\"reason\":\"Prueba\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/my/publications/1000000/official-verifications")
                .with(user(citizen))
                .with(csrf())
                .contentType("application/json")
                .content("{\"note\":\"Prueba\"}"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + citizen
                        + "\",\"action\":\"UNBLOCK\",\"reason\":\"Revisión completada\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocked").value(false))
        .andExpect(jsonPath("$.currentReason").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.history", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(jsonPath("$.history[0].action").value("UNBLOCK"))
        .andExpect(jsonPath("$.history[0].beforeBlocked").value(true))
        .andExpect(jsonPath("$.history[0].afterBlocked").value(false));

    // La sesión abierta recupera también la consulta SQL real de candidatos RA-14.
    mvc.perform(
            post("/api/my/publications/possible-duplicates")
                .session(existingSession)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Punto ficticio\",\"description\":\"Descripcion ficticia\",\"location\":\"Parque publico\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":40.1,\"longitude\":-70.1}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/publications")
                .with(user(citizen))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/operator/publications").with(user(citizen).roles("OPERATOR")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/my/verification-permission").with(user(citizen)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canVerify").value(true));
  }

  @Test
  void accountRestrictionRejectsSelfBlockDuplicateActionsAndRollsBackOnAuditFailure()
      throws Exception {
    String operator = account();
    String citizen = account();
    provision(operator, "GRANT", "Operador de prueba", UUID.randomUUID().toString());
    var actor = user(operator).roles("OPERATOR");

    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + operator
                        + "\",\"action\":\"BLOCK\",\"reason\":\"No bloquear actor\"}"))
        .andExpect(status().isConflict());

    jdbc.execute(
        "ALTER TABLE account_restriction_audit ADD CONSTRAINT ra16_audit_failure CHECK (reason <> 'FALLO')");
    try {
      mvc.perform(
              post("/api/operator/accounts/restriction")
                  .with(actor)
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      "{\"email\":\"" + citizen + "\",\"action\":\"BLOCK\",\"reason\":\"FALLO\"}"))
          .andExpect(status().isInternalServerError());
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM account_restriction WHERE account_id = ?",
              Integer.class,
              id(citizen)));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM account_restriction_audit WHERE account_id = ?",
              Integer.class,
              id(citizen)));
    } finally {
      jdbc.execute("ALTER TABLE account_restriction_audit DROP CONSTRAINT ra16_audit_failure");
    }

    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + citizen
                        + "\",\"action\":\"BLOCK\",\"reason\":\"Motivo inicial\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/operator/accounts/restriction")
                .with(actor)
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + citizen
                        + "\",\"action\":\"BLOCK\",\"reason\":\"Repetición\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void blockedOperatorImmediatelyLosesOperatorRoutesButCanReadPublicData() throws Exception {
    String email = account();
    provision(email, "GRANT", "Operador de prueba", UUID.randomUUID().toString());
    jdbc.update(
        "INSERT INTO account_restriction (account_id, blocked_by, reason, blocked_at)"
            + " VALUES (?, ?, ?, ?)",
        id(email),
        id(email),
        "Bloqueo de fixture",
        Timestamp.from(Instant.now()));

    mvc.perform(get("/operator").with(user(email).roles("OPERATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/operator/publications").with(user(email).roles("OPERATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/publications").with(user(email))).andExpect(status().isOk());
  }

  @Test
  void accountRestrictionAuditUsesAStableCursorAndExposesOlderEvents() throws Exception {
    String operator = account();
    String citizen = account();
    provision(operator, "GRANT", "Operador de prueba", UUID.randomUUID().toString());
    for (int index = 0; index < 26; index++) {
      restrictions.change(citizen, operator, "BLOCK", "Motivo de bloqueo " + index);
      restrictions.change(citizen, operator, "UNBLOCK", "Revisión " + index);
    }

    var latest = restrictions.inspect(citizen);
    assertFalse(latest.blocked());
    assertEquals(50, latest.history().size());
    assertTrue(latest.historyHasOlder());
    long cursor = latest.history().get(latest.history().size() - 1).id();
    var previous = restrictions.olderHistory(citizen, cursor, 50);
    assertEquals(2, previous.events().size());
    assertFalse(previous.hasOlder());
    // Comprueba también el contrato HTTP privado de ambas páginas, no solo el servicio.
    mvc.perform(
            get("/api/operator/accounts/restriction")
                .param("email", citizen)
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.history.length()").value(50))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    mvc.perform(
            get("/api/operator/accounts/restriction/history")
                .param("email", citizen)
                .param("beforeId", Long.toString(cursor))
                .with(user(operator).roles("OPERATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events.length()").value(2))
        .andExpect(jsonPath("$.hasOlder").value(false));
    assertTrue(
        latest.history().stream()
            .noneMatch(
                event -> previous.events().stream().anyMatch(old -> old.id() == event.id())));
  }

  /** Motivo obligatorio y límites se validan sin escribir estado ni auditoría. */
  @Test
  void restrictionRequiresReasonWithinLimits() throws Exception {
    String actor = account(), target = account();
    provision(actor, "GRANT", "Operador ficticio", UUID.randomUUID().toString());
    for (String reason : List.of("", "   ", "x".repeat(501))) {
      mvc.perform(
              post("/api/operator/accounts/restriction")
                  .with(user(actor).roles("OPERATOR"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      "{\"email\":\""
                          + target
                          + "\",\"action\":\"BLOCK\",\"reason\":\""
                          + reason
                          + "\"}"))
          .andExpect(status().isBadRequest());
    }
    assertFalse(restrictions.inspect(target).blocked());
    assertTrue(restrictions.inspect(target).history().isEmpty());
  }

  /** Dos conexiones independientes demuestran que el bloqueo de cuenta espera el commit SQL. */
  @Test
  void accountChangeWaitsForAccountLockAndSchemaCanBeReapplied() throws Exception {
    String actor = account(), target = account();
    provision(actor, "GRANT", "Operador ficticio", UUID.randomUUID().toString());
    javax.sql.DataSource source = jdbc.getDataSource();
    assertNotNull(source);
    try (var connection = source.getConnection()) {
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          connection, new org.springframework.core.io.ClassPathResource("schema.sql"));
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM citizen_account WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, id(target));
        statement.executeQuery().close();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      CountDownLatch started = new CountDownLatch(1);
      Future<?> decision =
          pool.submit(
              () -> {
                started.countDown();
                restrictions.change(target, actor, "BLOCK", "Decisión concurrente ficticia");
              });
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> decision.get(300, TimeUnit.MILLISECONDS));
        connection.commit();
        decision.get(10, TimeUnit.SECONDS);
        assertTrue(restrictions.inspect(target).blocked());
        assertEquals(1, restrictions.inspect(target).history().size());
      } finally {
        connection.rollback();
        pool.shutdownNow();
      }
    }
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

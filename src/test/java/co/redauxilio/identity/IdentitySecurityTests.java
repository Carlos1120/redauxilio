package co.redauxilio.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Comprueba registro, credenciales, bloqueo, sesión, rutas protegidas y CSRF con Spring real. */
@SpringBootTest
@AutoConfigureMockMvc
class IdentitySecurityTests {
  private static final String VALID_PASSWORD = "ClaveSegura123!";
  private static final Instant TEST_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private CitizenAccountRepository accounts;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.setInstant(TEST_INSTANT);
  }

  @Test
  void registrationNormalizesEmailAndStoresOnlyPasswordHash() throws Exception {
    String email = uniqueEmail("Citizen.User");

    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", " Nombre de prueba ")
                .param("email", email.toUpperCase())
                .param("password", VALID_PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=login"));

    CitizenAccount account = accounts.findByEmail(email).orElseThrow();
    assertEquals("Nombre de prueba", account.displayName());
    assertTrue(passwordEncoder.matches(VALID_PASSWORD, account.passwordHash()));
    assertFalse(VALID_PASSWORD.equals(account.passwordHash()));
  }

  @Test
  void duplicateEmailAndInvalidRegistrationAreRejected() throws Exception {
    String email = uniqueEmail("duplicate");
    register(email);

    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "Otra cuenta")
                .param("email", email.toUpperCase())
                .param("password", VALID_PASSWORD))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                .attribute("identityMode", "register"));

    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "Cuenta inválida")
                .param("email", "no-es-un-correo")
                .param("password", "corta"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"));

    assertTrue(accounts.findByEmail("no-es-un-correo").isEmpty());
  }

  @Test
  void incorrectPasswordReturnsGenericFailureAndDoesNotAuthenticate() throws Exception {
    String email = uniqueEmail("wrong-password");
    register(email);

    mvc.perform(
            post("/login").with(csrf()).param("username", email).param("password", "incorrecta"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=login&error"));
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", uniqueEmail("unknown"))
                .param("password", "incorrecta"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=login&error"));

    assertEquals(1, accounts.findByEmail(email).orElseThrow().failedLoginAttempts());
  }

  @Test
  void accountLocksOnFifthFailureAndDoesNotCountAttemptsWhileLocked() throws Exception {
    String email = uniqueEmail("locked");
    register(email);

    for (int attempt = 1; attempt <= CitizenAccountService.MAX_FAILED_ATTEMPTS; attempt++) {
      mvc.perform(
              post("/login").with(csrf()).param("username", email).param("password", "incorrecta"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/?access=login&error"));

      CitizenAccount account = accounts.findByEmail(email).orElseThrow();
      assertEquals(attempt, account.failedLoginAttempts());
      if (attempt < CitizenAccountService.MAX_FAILED_ATTEMPTS) {
        assertNull(account.lockedUntil());
      } else {
        assertEquals(TEST_INSTANT.plus(CitizenAccountService.LOCK_DURATION), account.lockedUntil());
      }
    }

    clock.setInstant(TEST_INSTANT.plus(CitizenAccountService.LOCK_DURATION).minusNanos(1));
    mvc.perform(
            post("/login").with(csrf()).param("username", email).param("password", VALID_PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=login&error"));
    assertEquals(
        CitizenAccountService.MAX_FAILED_ATTEMPTS,
        accounts.findByEmail(email).orElseThrow().failedLoginAttempts());

    clock.setInstant(TEST_INSTANT.plus(CitizenAccountService.LOCK_DURATION));
    mvc.perform(
            post("/login").with(csrf()).param("username", email).param("password", VALID_PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=account"));

    CitizenAccount account = accounts.findByEmail(email).orElseThrow();
    assertEquals(0, account.failedLoginAttempts());
    assertNull(account.lockedUntil());
  }

  @Test
  void privateRouteRequiresAuthenticationAndLogoutInvalidatesSession() throws Exception {
    String email = uniqueEmail("private-route");
    register(email);
    mvc.perform(get("/account"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("http://localhost/login"));
    mvc.perform(get("/api/publications")).andExpect(status().isOk());

    MvcResult login =
        mvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", email)
                    .param("password", VALID_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/?access=account"))
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertNotNull(session);
    mvc.perform(get("/account").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=account"));

    mvc.perform(post("/logout").with(csrf()).session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=login&signedout"));
    mvc.perform(get("/account").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("http://localhost/login"));
  }

  @Test
  void sessionExpiresAtTwentyFourHoursDespiteRecentActivity() throws Exception {
    String email = uniqueEmail("session-expiry");
    register(email);
    MvcResult login =
        mvc.perform(
                post("/login")
                    .with(csrf())
                    .param("username", email)
                    .param("password", VALID_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertNotNull(session);

    clock.setInstant(TEST_INSTANT.plus(Duration.ofHours(23)));
    mvc.perform(get("/account").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/?access=account"));

    clock.setInstant(TEST_INSTANT.plus(Duration.ofHours(24)));
    mvc.perform(get("/account").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?expired"));
    assertTrue(session.isInvalid());
  }

  @Test
  void stateChangingFormsRejectRequestsWithoutCsrf() throws Exception {
    mvc.perform(
            post("/register")
                .param("displayName", "Cuenta")
                .param("email", uniqueEmail("csrf"))
                .param("password", VALID_PASSWORD))
        .andExpect(status().isForbidden());
    mvc.perform(post("/login").param("username", uniqueEmail("csrf-login")).param("password", "x"))
        .andExpect(status().isForbidden());
  }

  private void register(String email) throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "Ciudadano de prueba")
                .param("email", email)
                .param("password", VALID_PASSWORD))
        .andExpect(status().is3xxRedirection());
  }

  private String uniqueEmail(String prefix) {
    return (prefix + "." + UUID.randomUUID() + "@example.test").toLowerCase(Locale.ROOT);
  }

  /** Reloj controlable de pruebas para reproducir los límites de bloqueo y sesión. */
  static class MutableClock extends Clock {
    private Instant instant = TEST_INSTANT;

    void setInstant(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  @TestConfiguration
  static class TestClockConfiguration {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }
}

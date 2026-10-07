package co.redauxilio.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de registro y bloqueo temporal; recibe correo normalizado y contraseña en texto solo al
 * autenticar.
 */
@Service
class CitizenAccountService {
  static final int MAX_FAILED_ATTEMPTS = 5;
  static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private final CitizenAccountRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  CitizenAccountService(
      CitizenAccountRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  void register(String email, String displayName, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException("La contraseña supera el límite permitido.");
    }
    repository.create(
        normalizeEmail(email),
        displayName.trim(),
        passwordEncoder.encode(password),
        clock.instant());
  }

  @Transactional
  void recordFailedAttempt(String email) {
    if (email == null || email.isBlank()) return;
    String normalizedEmail = normalizeEmail(email);
    repository
        .lockStateForUpdate(normalizedEmail)
        .ifPresent(
            state -> {
              Instant now = clock.instant();
              if (state.lockedUntil() != null && now.isBefore(state.lockedUntil())) return;
              int attempts = (state.lockedUntil() != null ? 0 : state.failedAttempts()) + 1;
              Instant lockedUntil =
                  attempts >= MAX_FAILED_ATTEMPTS ? now.plus(LOCK_DURATION) : null;
              repository.updateFailureState(normalizedEmail, attempts, lockedUntil);
            });
  }

  @Transactional
  void recordSuccessfulLogin(String email) {
    repository.resetFailureState(normalizeEmail(email));
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}

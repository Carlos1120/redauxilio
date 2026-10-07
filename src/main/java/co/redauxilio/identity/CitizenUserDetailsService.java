package co.redauxilio.identity;

import java.time.Instant;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Adapta la cuenta persistida al modelo de autenticación de Spring Security. */
@Service
class CitizenUserDetailsService implements UserDetailsService {
  private final CitizenAccountRepository repository;

  CitizenUserDetailsService(CitizenAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    CitizenAccount account =
        repository
            .findByEmail(CitizenAccountService.normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
    boolean locked = account.lockedUntil() != null && Instant.now().isBefore(account.lockedUntil());
    return User.withUsername(account.email())
        .password(account.passwordHash())
        .roles("CITIZEN")
        .accountLocked(locked)
        .build();
  }
}

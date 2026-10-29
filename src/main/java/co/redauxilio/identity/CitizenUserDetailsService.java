package co.redauxilio.identity;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Adapta la cuenta y el permiso SQL al iniciar sesión; el registro no concede roles internos. */
@Service
class CitizenUserDetailsService implements UserDetailsService {
  private final CitizenAccountRepository repository;
  private final java.time.Clock clock;
  private final OperatorPermissions operators;

  CitizenUserDetailsService(
      CitizenAccountRepository repository, java.time.Clock clock, OperatorPermissions operators) {
    this.repository = repository;
    this.clock = clock;
    this.operators = operators;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    CitizenAccount account =
        repository
            .findByEmail(CitizenAccountService.normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
    boolean locked =
        account.lockedUntil() != null && clock.instant().isBefore(account.lockedUntil());
    return User.withUsername(account.email())
        .password(account.passwordHash())
        .roles(
            operators.isOperator(account.email())
                ? new String[] {"CITIZEN", "OPERATOR"}
                : new String[] {"CITIZEN"})
        .accountLocked(locked)
        .build();
  }
}

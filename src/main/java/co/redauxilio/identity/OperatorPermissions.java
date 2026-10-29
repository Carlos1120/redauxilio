package co.redauxilio.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permiso persistente del operador y aprovisionamiento interno; ninguna operación es un endpoint
 * público.
 */
@Service
public class OperatorPermissions {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  OperatorPermissions(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Lee el permiso vigente y el bloqueo temporal, sin confiar en un rol guardado en una sesión
   * antigua.
   */
  public boolean isOperator(String email) {
    if (email == null || email.isBlank()) return false;
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM operator_permission p JOIN citizen_account a ON a.id = p.account_id"
                + " WHERE a.email = ? AND p.role = 'ROLE_OPERATOR' AND (a.locked_until IS NULL OR a.locked_until <= ?)",
            Integer.class,
            email.trim().toLowerCase(Locale.ROOT),
            Timestamp.from(clock.instant()))
        > 0;
  }

  /**
   * Exige sesión con ROLE_OPERATOR y permiso actual en SQL: una revocación afecta la siguiente
   * petición.
   */
  public boolean allows(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"))
        && isOperator(authentication.getName());
  }

  /**
   * Configuración controlada: cuenta existente por ID y correo esperado, actor, motivo y UUID
   * únicos. Bloquea la cuenta; permiso y fotografía anterior/posterior de auditoría se confirman o
   * revierten juntos. Repetir el UUID con los mismos datos no vuelve a conceder un permiso revocado
   * posteriormente.
   */
  @Transactional
  public void provision(
      long accountId,
      String expectedEmail,
      String action,
      String actor,
      String reason,
      String requestId) {
    String email = required(expectedEmail, 254).toLowerCase(Locale.ROOT);
    String validAction = required(action, 8);
    String validActor = required(actor, 80);
    String validReason = required(reason, 500);
    String key = UUID.fromString(required(requestId, 36)).toString();
    if (accountId <= 0 || !(validAction.equals("GRANT") || validAction.equals("REVOKE")))
      throw new IllegalArgumentException("Aprovisionamiento de operador inválido");
    var account =
        jdbc.query(
            "SELECT email FROM citizen_account WHERE id = ? FOR UPDATE",
            (r, i) -> r.getString(1),
            accountId);
    if (account.isEmpty() || !account.get(0).equals(email))
      throw new IllegalArgumentException(
          "La cuenta existente no coincide con la configuración controlada");
    var previous =
        jdbc.query(
            "SELECT account_id, action, actor, reason FROM operator_permission_audit WHERE request_id = ?",
            (r, i) ->
                r.getLong(1) == accountId
                    && r.getString(2).equals(validAction)
                    && r.getString(3).equals(validActor)
                    && r.getString(4).equals(validReason),
            key);
    if (!previous.isEmpty()) {
      if (!previous.get(0))
        throw new IllegalArgumentException(
            "UUID de aprovisionamiento ya utilizado con otros datos");
      return;
    }
    boolean before =
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM operator_permission WHERE account_id = ?",
                Integer.class,
                accountId)
            > 0;
    boolean after = validAction.equals("GRANT");
    if (after && !before)
      jdbc.update(
          "INSERT INTO operator_permission (account_id, role, granted_at) VALUES (?, 'ROLE_OPERATOR', ?)",
          accountId,
          Timestamp.from(clock.instant()));
    if (!after) jdbc.update("DELETE FROM operator_permission WHERE account_id = ?", accountId);
    jdbc.update(
        "INSERT INTO operator_permission_audit (request_id, account_id, actor, action, before_granted, after_granted, reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        key,
        accountId,
        validActor,
        validAction,
        before,
        after,
        validReason,
        Timestamp.from(clock.instant()));
  }

  private static String required(String value, int maximum) {
    if (value == null || value.isBlank() || value.strip().length() > maximum)
      throw new IllegalArgumentException("Faltan datos válidos de aprovisionamiento controlado");
    return value.strip();
  }
}

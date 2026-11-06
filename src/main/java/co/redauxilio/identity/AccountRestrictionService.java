package co.redauxilio.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Aplica bloqueos administrativos persistentes y conserva cada decisión con motivo y actor. */
@Service
public class AccountRestrictionService {
  private static final int HISTORY_LIMIT = 50;

  private final JdbcTemplate jdbc;
  private final OperatorPermissions operators;
  private final Clock clock;

  AccountRestrictionService(JdbcTemplate jdbc, OperatorPermissions operators, Clock clock) {
    this.jdbc = jdbc;
    this.operators = operators;
    this.clock = clock;
  }

  /** Impide las escrituras colaborativas a cuentas bloqueadas sin cerrar la lectura pública. */
  public boolean allowsCollaboration(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)
        && !isBlocked(authentication.getName());
  }

  /** Consulta el estado de una cuenta y sus 50 cambios más recientes para el operador. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AccountSnapshot inspect(String email) {
    String normalized = normalizedEmail(email);
    List<AccountRow> rows =
        jdbc.query(
            "SELECT a.email, a.display_name, r.blocked_at, r.reason"
                + " FROM citizen_account a LEFT JOIN account_restriction r ON r.account_id = a.id"
                + " WHERE a.email = ?",
            (row, index) ->
                new AccountRow(
                    row.getString("email"),
                    row.getString("display_name"),
                    row.getTimestamp("blocked_at") != null,
                    toInstant(row.getTimestamp("blocked_at")),
                    row.getString("reason")),
            normalized);
    if (rows.isEmpty()) throw unavailable();
    AccountRow account = rows.get(0);
    HistoryPage history = history(normalized, null, HISTORY_LIMIT);
    return new AccountSnapshot(
        account.email(),
        account.displayName(),
        account.blocked(),
        account.blockedAt(),
        account.currentReason(),
        history.events(),
        history.hasOlder());
  }

  /** Consulta páginas anteriores de auditoría usando un cursor descendente sin repetir eventos. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public HistoryPage olderHistory(String email, long beforeId, int limit) {
    String normalized = normalizedEmail(email);
    if (beforeId <= 0 || limit < 1 || limit > HISTORY_LIMIT)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    boolean exists =
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM citizen_account WHERE email = ?", Integer.class, normalized)
            > 0;
    if (!exists) throw unavailable();
    return history(normalized, beforeId, limit);
  }

  /**
   * Bloquea o reactiva una cuenta en forma explícita y registra el cambio en la misma transacción.
   */
  @Transactional
  public AccountSnapshot change(
      String targetEmail, String actorEmail, String action, String reason) {
    String target = normalizedEmail(targetEmail);
    String actor = normalizedEmail(actorEmail);
    String detail = requiredReason(reason);
    String operation = action == null ? "" : action.strip().toUpperCase(Locale.ROOT);
    if (!(operation.equals("BLOCK") || operation.equals("UNBLOCK")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    if (target.equals(actor))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes bloquear tu propia cuenta");
    Long targetId =
        jdbc
            .query(
                "SELECT id FROM citizen_account WHERE email = ?",
                (row, index) -> row.getLong(1),
                target)
            .stream()
            .findFirst()
            .orElseThrow(AccountRestrictionService::unavailable);
    Long actorId =
        jdbc
            .query(
                "SELECT id FROM citizen_account WHERE email = ?",
                (row, index) -> row.getLong(1),
                actor)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    // Bloquea actor y objetivo en orden estable. Una revocación/bloqueo concurrente del operador
    // espera este cambio, y dos cambios cruzados no toman las filas en orden inverso.
    jdbc.query(
        "SELECT id FROM citizen_account WHERE id IN (?, ?) ORDER BY id FOR UPDATE",
        (row, index) -> row.getLong(1),
        targetId,
        actorId);
    if (!operators.isOperator(actor))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permiso de operador no disponible");
    boolean before =
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_restriction WHERE account_id = ?",
                Integer.class,
                targetId)
            > 0;
    boolean after = operation.equals("BLOCK");
    if (before == after)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta ya tiene ese estado");

    Instant now = clock.instant();
    if (after) {
      jdbc.update(
          "INSERT INTO account_restriction (account_id, blocked_by, reason, blocked_at)"
              + " VALUES (?, ?, ?, ?)",
          targetId,
          actorId,
          detail,
          Timestamp.from(now));
    } else {
      jdbc.update("DELETE FROM account_restriction WHERE account_id = ?", targetId);
    }
    jdbc.update(
        "INSERT INTO account_restriction_audit"
            + " (account_id, actor_id, action, before_blocked, after_blocked, reason, changed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        targetId,
        actorId,
        operation,
        before,
        after,
        detail,
        Timestamp.from(now));
    return inspect(target);
  }

  /** Revalida una sesión en cada escritura para que el bloqueo entre en vigor sin nuevo inicio. */
  private boolean isBlocked(String email) {
    if (email == null || email.isBlank()) return false;
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_restriction r JOIN citizen_account a ON a.id = r.account_id"
                + " WHERE a.email = ?",
            Integer.class,
            email.trim().toLowerCase(Locale.ROOT))
        > 0;
  }

  private HistoryPage history(String email, Long beforeId, int limit) {
    String cursor = beforeId == null ? "" : " AND e.id < ?";
    Object[] parameters =
        beforeId == null
            ? new Object[] {email, limit + 1}
            : new Object[] {email, beforeId, limit + 1};
    List<AuditEntry> rows =
        jdbc.query(
            "SELECT e.id, e.action, e.before_blocked, e.after_blocked, e.reason, e.changed_at, a.id AS actor_id, a.display_name"
                + " FROM account_restriction_audit e"
                + " JOIN citizen_account target ON target.id = e.account_id"
                + " JOIN citizen_account a ON a.id = e.actor_id"
                + " WHERE target.email = ?"
                + cursor
                + " ORDER BY e.id DESC LIMIT ?",
            (row, index) ->
                new AuditEntry(
                    row.getLong("id"),
                    row.getString("action"),
                    row.getBoolean("before_blocked"),
                    row.getBoolean("after_blocked"),
                    row.getString("reason"),
                    toInstant(row.getTimestamp("changed_at")),
                    row.getLong("actor_id"),
                    row.getString("display_name")),
            parameters);
    boolean hasOlder = rows.size() > limit;
    List<AuditEntry> events = hasOlder ? rows.subList(0, limit) : rows;
    return new HistoryPage(List.copyOf(events), hasOlder);
  }

  private static String normalizedEmail(String email) {
    if (email == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    String value = email.strip().toLowerCase(Locale.ROOT);
    if (value.isEmpty() || value.length() > 254 || value.chars().anyMatch(Character::isWhitespace))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return value;
  }

  private static String requiredReason(String reason) {
    String detail = reason == null ? "" : reason.strip();
    if (detail.isEmpty() || detail.length() > 500)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return detail;
  }

  private static Instant toInstant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no disponible");
  }

  public record AccountSnapshot(
      String email,
      String displayName,
      boolean blocked,
      Instant blockedAt,
      String currentReason,
      List<AuditEntry> history,
      boolean historyHasOlder) {}

  public record HistoryPage(List<AuditEntry> events, boolean hasOlder) {}

  public record AuditEntry(
      long id,
      String action,
      boolean beforeBlocked,
      boolean afterBlocked,
      String reason,
      Instant changedAt,
      long actorId,
      String actorDisplayName) {}

  private record AccountRow(
      String email, String displayName, boolean blocked, Instant blockedAt, String currentReason) {}
}

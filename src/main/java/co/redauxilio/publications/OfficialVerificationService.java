package co.redauxilio.publications;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gestiona permisos y verificaciones oficiales sin alterar el respaldo comunitario. */
@Service
class OfficialVerificationService {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final co.redauxilio.identity.OperatorPermissions operators;

  OfficialVerificationService(
      JdbcTemplate jdbc, Clock clock, co.redauxilio.identity.OperatorPermissions operators) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.operators = operators;
  }

  /** Indica si la cuenta tiene permiso persistido y no está bloqueada. */
  boolean canVerify(String email) {
    if (email == null || email.isBlank()) return false;
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM verification_permission v JOIN citizen_account a ON a.id = v.account_id WHERE a.email = ? AND (a.locked_until IS NULL OR a.locked_until <= ?) AND NOT EXISTS (SELECT 1 FROM account_restriction r WHERE r.account_id = a.id)",
            Integer.class,
            email.trim().toLowerCase(Locale.ROOT),
            Timestamp.from(clock.instant()))
        > 0;
  }

  /** Concede o retira una autorización, dejando el cambio y su motivo en la misma transacción. */
  @Transactional
  boolean changePermission(String targetEmail, String actorEmail, String action, String reason) {
    String email = required(targetEmail, 254, "Indica el correo de la cuenta existente.");
    String detail = required(reason, 500, "El motivo es obligatorio.");
    if (!"GRANT".equals(action) && !"REVOKE".equals(action))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var target = lockAccount(email);
    if (target == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    long actorId = accountId(actorEmail);
    Boolean before =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM verification_permission WHERE account_id = ?",
            Boolean.class,
            target.id());
    boolean after = "GRANT".equals(action);
    if (Boolean.TRUE.equals(before) == after) return after;
    if (after) {
      jdbc.update(
          "INSERT INTO verification_permission (account_id, granted_by, granted_at) VALUES (?, ?, ?)",
          target.id(),
          actorId,
          Timestamp.from(clock.instant()));
    } else {
      jdbc.update("DELETE FROM verification_permission WHERE account_id = ?", target.id());
    }
    jdbc.update(
        "INSERT INTO verification_permission_audit (account_id, actor_id, action, before_granted, after_granted, reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        target.id(),
        actorId,
        action,
        Boolean.TRUE.equals(before),
        after,
        detail,
        Timestamp.from(clock.instant()));
    return after;
  }

  /** Registra el respaldo para la versión actual y conserva el evento como historial inmutable. */
  @Transactional
  long verify(long publicationId, String actorEmail, String note, String reference) {
    return recordVerification(
        publicationId, actorEmail, note, reference, "Verificación oficial registrada", false);
  }

  /** Registra una corrección iniciada por operador y exige el motivo administrativo. */
  @Transactional
  long verifyAsOperator(
      long publicationId, String actorEmail, String note, String reference, String reason) {
    return recordVerification(
        publicationId,
        actorEmail,
        note,
        reference,
        required(reason, 500, "El motivo de la corrección es obligatorio."),
        true);
  }

  private long recordVerification(
      long publicationId,
      String actorEmail,
      String note,
      String reference,
      String reason,
      boolean operatorCorrection) {
    String description = note == null ? "" : note.strip();
    if (description.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    String source = optional(reference, 200);
    if (description.isBlank() && source == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    AccountRow actor = lockAccount(actorEmail);
    if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    long actorId = actor.id();
    if (operatorCorrection ? !operators.isOperator(actorEmail) : !canVerify(actorEmail))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    PublicationRow publication = lockPublication(publicationId);
    if (publication == null || !publication.visible())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (hasActiveVerification(publicationId, publication.version(), actorId))
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "INSERT INTO official_verification (publication_id, publication_version, verifier_id, note, reference, verified_at) VALUES (?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          statement.setLong(1, publicationId);
          statement.setLong(2, publication.version());
          statement.setLong(3, actorId);
          statement.setString(4, description);
          statement.setString(5, source);
          statement.setTimestamp(6, Timestamp.from(clock.instant()));
          return statement;
        },
        keys);
    Number generatedId = keys.getKey();
    if (generatedId == null)
      throw new IllegalStateException("La verificación no obtuvo identificador.");
    long verificationId = generatedId.longValue();
    jdbc.update(
        "INSERT INTO official_verification_action (verification_id, actor_id, action, reason, changed_at) VALUES (?, ?, 'VERIFY', ?, ?)",
        verificationId,
        actorId,
        reason,
        Timestamp.from(clock.instant()));
    return verificationId;
  }

  /** Permite al operador retirar una verificación por corrección con motivo auditable. */
  @Transactional
  void revoke(long publicationId, long verificationId, String actorEmail, String reason) {
    String detail = required(reason, 500, "El motivo de retiro es obligatorio.");
    if (!operators.isOperator(actorEmail)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    long actorId = accountId(actorEmail);
    List<Long> active =
        jdbc.query(
            "SELECT v.id FROM official_verification v JOIN citizen_publication p ON p.id = v.publication_id WHERE v.id = ? AND v.publication_id = ? AND NOT EXISTS (SELECT 1 FROM official_verification_action a WHERE a.verification_id = v.id AND a.action = 'REVOKE') FOR UPDATE",
            (row, index) -> row.getLong(1),
            verificationId,
            publicationId);
    if (active.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    jdbc.update(
        "INSERT INTO official_verification_action (verification_id, actor_id, action, reason, changed_at) VALUES (?, ?, 'REVOKE', ?, ?)",
        verificationId,
        actorId,
        detail,
        Timestamp.from(clock.instant()));
  }

  /** Lista datos de respaldo sin correo ni ID interno; solo las verificaciones aún vigentes. */
  List<PublicVerification> listPublic(long publicationId) {
    return jdbc.query(
        "SELECT v.id, a.display_name, v.note, v.reference, v.verified_at FROM official_verification v JOIN citizen_account a ON a.id = v.verifier_id JOIN citizen_publication p ON p.id = v.publication_id WHERE v.publication_id = ? AND v.publication_version = p.confirmation_version AND p.visible = TRUE AND NOT EXISTS (SELECT 1 FROM official_verification_action x WHERE x.verification_id = v.id AND x.action = 'REVOKE') ORDER BY v.verified_at DESC, v.id DESC",
        (row, index) ->
            new PublicVerification(
                row.getString("display_name"),
                row.getString("note"),
                row.getString("reference"),
                row.getTimestamp("verified_at").toInstant().toString()),
        publicationId);
  }

  /** Expone al operador todas las verificaciones, incluso retiradas o de versiones anteriores. */
  List<VerificationHistory> history(long publicationId) {
    return jdbc.query(
        "SELECT v.id, v.publication_version, a.display_name, v.note, v.reference, v.verified_at, (SELECT COUNT(*) FROM official_verification_action x WHERE x.verification_id = v.id AND x.action = 'REVOKE') > 0 AS revoked, (SELECT r.display_name FROM official_verification_action x JOIN citizen_account r ON r.id = x.actor_id WHERE x.verification_id = v.id AND x.action = 'REVOKE' ORDER BY x.id DESC LIMIT 1) AS revoked_by, (SELECT x.reason FROM official_verification_action x WHERE x.verification_id = v.id AND x.action = 'REVOKE' ORDER BY x.id DESC LIMIT 1) AS revoke_reason, (SELECT x.changed_at FROM official_verification_action x WHERE x.verification_id = v.id AND x.action = 'REVOKE' ORDER BY x.id DESC LIMIT 1) AS revoked_at FROM official_verification v JOIN citizen_account a ON a.id = v.verifier_id WHERE v.publication_id = ? ORDER BY v.id DESC LIMIT 50",
        (row, index) ->
            new VerificationHistory(
                row.getLong("id"),
                row.getLong("publication_version"),
                row.getString("display_name"),
                row.getString("note"),
                row.getString("reference"),
                row.getTimestamp("verified_at").toInstant().toString(),
                row.getBoolean("revoked"),
                row.getString("revoked_by"),
                row.getString("revoke_reason"),
                row.getTimestamp("revoked_at") == null
                    ? null
                    : row.getTimestamp("revoked_at").toInstant().toString()),
        publicationId);
  }

  /** Lee eventos recientes de permisos con identidad de actor solo en el panel privado. */
  List<PermissionAudit> permissionHistory(int limit, int offset) {
    int boundedLimit = Math.max(1, Math.min(limit, 50));
    int boundedOffset = Math.max(0, offset);
    return jdbc.query(
        "SELECT h.account_id, target.email, actor.email AS actor_email, h.action, h.before_granted, h.after_granted, h.reason, h.changed_at FROM verification_permission_audit h JOIN citizen_account target ON target.id = h.account_id JOIN citizen_account actor ON actor.id = h.actor_id ORDER BY h.id DESC LIMIT ? OFFSET ?",
        (row, index) ->
            new PermissionAudit(
                row.getLong("account_id"),
                row.getString("email"),
                row.getString("actor_email"),
                row.getString("action"),
                row.getBoolean("before_granted"),
                row.getBoolean("after_granted"),
                row.getString("reason"),
                row.getTimestamp("changed_at").toInstant().toString()),
        boundedLimit,
        boundedOffset);
  }

  /** Proyecta cuentas autorizadas para que el operador pueda revisar y retirar su permiso. */
  List<Verifier> activeVerifiers() {
    return jdbc.query(
        "SELECT a.id, a.email, a.display_name, v.granted_at FROM verification_permission v JOIN citizen_account a ON a.id = v.account_id ORDER BY a.display_name, a.id",
        (row, index) ->
            new Verifier(
                row.getLong("id"),
                row.getString("email"),
                row.getString("display_name"),
                row.getTimestamp("granted_at").toInstant().toString()));
  }

  private boolean hasActiveVerification(long publicationId, long version, long accountId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM official_verification v WHERE v.publication_id = ? AND v.publication_version = ? AND v.verifier_id = ? AND NOT EXISTS (SELECT 1 FROM official_verification_action x WHERE x.verification_id = v.id AND x.action = 'REVOKE')",
            Integer.class,
            publicationId,
            version,
            accountId)
        > 0;
  }

  private PublicationRow lockPublication(long id) {
    return jdbc
        .query(
            "SELECT confirmation_version, visible FROM citizen_publication WHERE id = ? FOR UPDATE",
            (row, index) -> new PublicationRow(row.getLong(1), row.getBoolean(2)),
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private AccountRow lockAccount(String email) {
    return jdbc
        .query(
            "SELECT id, email FROM citizen_account WHERE email = ? FOR UPDATE",
            (row, index) -> new AccountRow(row.getLong(1), row.getString(2)),
            email.toLowerCase(Locale.ROOT))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private long accountId(String email) {
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM citizen_account WHERE email = ?",
            Long.class,
            email.trim().toLowerCase(Locale.ROOT));
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return id;
  }

  private static String required(String value, int max, String message) {
    if (value == null || value.isBlank() || value.strip().length() > max)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    return value.strip();
  }

  private static String optional(String value, int max) {
    if (value == null || value.isBlank()) return null;
    if (value.strip().length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return value.strip();
  }

  record PublicVerification(String accountName, String note, String reference, String verifiedAt) {}

  record VerificationHistory(
      long id,
      long publicationVersion,
      String accountName,
      String note,
      String reference,
      String verifiedAt,
      boolean revoked,
      String revokedBy,
      String revokeReason,
      String revokedAt) {}

  record PermissionAudit(
      long accountId,
      String email,
      String actorEmail,
      String action,
      boolean beforeGranted,
      boolean afterGranted,
      String reason,
      String changedAt) {}

  record Verifier(long accountId, String email, String displayName, String grantedAt) {}

  private record PublicationRow(long version, boolean visible) {}

  private record AccountRow(long id, String email) {}
}

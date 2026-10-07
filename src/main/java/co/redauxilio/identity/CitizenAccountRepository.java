package co.redauxilio.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Acceso parametrizado a las cuentas ciudadanas y a sus contadores de autenticación. */
@Repository
class CitizenAccountRepository {
  private static final RowMapper<CitizenAccount> ACCOUNT_MAPPER =
      CitizenAccountRepository::mapAccount;

  private final JdbcTemplate jdbc;

  CitizenAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<CitizenAccount> findByEmail(String email) {
    return jdbc
        .query("SELECT * FROM citizen_account WHERE email = ?", ACCOUNT_MAPPER, email)
        .stream()
        .findFirst();
  }

  void create(String email, String displayName, String passwordHash, Instant createdAt) {
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at)"
            + " VALUES (?, ?, ?, ?)",
        email,
        displayName,
        passwordHash,
        Timestamp.from(createdAt));
  }

  Optional<AccountLockState> lockStateForUpdate(String email) {
    return jdbc
        .query(
            "SELECT failed_login_attempts, locked_until FROM citizen_account WHERE email = ? FOR UPDATE",
            (result, row) ->
                new AccountLockState(
                    result.getInt("failed_login_attempts"), toInstant(result, "locked_until")),
            email)
        .stream()
        .findFirst();
  }

  void updateFailureState(String email, int attempts, Instant lockedUntil) {
    jdbc.update(
        "UPDATE citizen_account SET failed_login_attempts = ?, locked_until = ? WHERE email = ?",
        attempts,
        lockedUntil == null ? null : Timestamp.from(lockedUntil),
        email);
  }

  void resetFailureState(String email) {
    jdbc.update(
        "UPDATE citizen_account SET failed_login_attempts = 0, locked_until = NULL WHERE email = ?",
        email);
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private static CitizenAccount mapAccount(ResultSet result, int row) throws SQLException {
    return new CitizenAccount(
        result.getLong("id"),
        result.getString("email"),
        result.getString("display_name"),
        result.getString("password_hash"),
        result.getInt("failed_login_attempts"),
        toInstant(result, "locked_until"),
        toInstant(result, "created_at"));
  }

  private static Instant toInstant(ResultSet result, String column) throws SQLException {
    Timestamp timestamp = result.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  record AccountLockState(int failedAttempts, Instant lockedUntil) {}
}

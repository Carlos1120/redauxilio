package co.redauxilio.publications;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Núcleo independiente RA-05: confirma versiones persistidas, controla cuota móvil y guarda
 * historia. Bloquea primero la cuenta y después la publicación: serializa duplicados, cupo e
 * invalidación. No modifica estado operativo, visibilidad ni concede verificación oficial.
 */
@Service
class PublicationConfirmationService {
  private final JdbcTemplate jdbc;
  private final CitizenPublicationRepository accounts;
  private final Clock clock;

  PublicationConfirmationService(
      JdbcTemplate jdbc, CitizenPublicationRepository accounts, Clock clock) {
    this.jdbc = jdbc;
    this.accounts = accounts;
    this.clock = clock;
  }

  /**
   * Recibe el ID público y el correo de sesión; nunca acepta un confirmador enviado por cliente.
   */
  @Transactional
  void confirm(long id, String email, long expectedVersion) {
    long confirmer =
        accounts
            .lockAuthor(email)
            .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Inicia sesión para confirmar"));
    Version publication = lockPublication(id);
    if (publication.version() != expectedVersion)
      throw error(HttpStatus.CONFLICT, "La publicación cambió; actualiza antes de confirmar");
    if (publication.authorId() == confirmer)
      throw error(HttpStatus.FORBIDDEN, "El autor no puede confirmar su publicación");
    if (publication.closed())
      throw error(HttpStatus.CONFLICT, "Un caso cerrado no admite confirmaciones");
    Integer existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation WHERE publication_id = ? AND publication_version = ? AND confirmer_id = ?",
            Integer.class,
            id,
            publication.version(),
            confirmer);
    if (existing != null && existing > 0)
      throw error(HttpStatus.CONFLICT, "Ya confirmaste esta versión");
    var now = clock.instant();
    Integer accepted =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation WHERE confirmer_id = ? AND confirmed_at > ?",
            Integer.class,
            confirmer,
            Timestamp.from(now.minus(Duration.ofHours(1))));
    if (accepted != null && accepted >= 30)
      throw error(HttpStatus.TOO_MANY_REQUESTS, "Máximo 30 confirmaciones aceptadas por hora");
    jdbc.update(
        "INSERT INTO publication_confirmation (publication_id, publication_version, confirmer_id, confirmed_at) VALUES (?, ?, ?, ?)",
        id,
        publication.version(),
        confirmer,
        Timestamp.from(now));
  }

  /**
   * Punto de integración diferido con RA-04: invocar en su MISMA transacción tras un cambio
   * sustancial. Solo el autor invalida; incrementa versión y registra actor/motivo/fecha sin borrar
   * confirmaciones. No está expuesto por HTTP. No sustituye detectar qué campos cambiaron en
   * edición/transición.
   */
  @Transactional
  void invalidateForSubstantialChange(long id, String actorEmail, String reason) {
    Version publication = lockPublication(id);
    Long actor =
        jdbc
            .queryForList("SELECT id FROM citizen_account WHERE email = ?", Long.class, actorEmail)
            .stream()
            .findFirst()
            .orElse(null);
    if (actor == null || actor != publication.authorId())
      throw error(HttpStatus.FORBIDDEN, "Solo el autor puede invalidar esta versión");
    if (reason == null || reason.isBlank() || reason.trim().length() > 500)
      throw error(HttpStatus.BAD_REQUEST, "Indica un motivo de hasta 500 caracteres");
    jdbc.update(
        "UPDATE citizen_publication SET confirmation_version = confirmation_version + 1 WHERE id = ?",
        id);
    jdbc.update(
        "INSERT INTO publication_confirmation_invalidation (publication_id, actor_id, previous_version, next_version, reason, invalidated_at) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        actor,
        publication.version(),
        publication.version() + 1,
        reason.trim(),
        Timestamp.from(clock.instant()));
  }

  private Version lockPublication(long id) {
    return jdbc
        .query(
            "SELECT author_id, confirmation_version, closed, category, operational_status"
                + " FROM citizen_publication WHERE id = ? AND visible = TRUE FOR UPDATE",
            (row, index) ->
                new Version(
                    row.getLong("author_id"),
                    row.getLong("confirmation_version"),
                    // La vía HABILITADA heredada está cerrada aunque conserve closed=false.
                    row.getBoolean("closed")
                        || ("ROAD".equals(row.getString("category"))
                            && "HABILITADA".equals(row.getString("operational_status")))),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Publicación no disponible"));
  }

  private static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }

  private record Version(long authorId, long version, boolean closed) {}
}

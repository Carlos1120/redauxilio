package co.redauxilio.publications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registra denuncias atribuibles y revisión por tres ciudadanos independientes, sin alterar
 * confianza. Bloquea cuenta y publicación en ese orden; registro, umbral y auditoría son una
 * transacción. Permite casos cerrados visibles. RA-08 resolverá denuncias sin eliminar su historia.
 */
@Service
class PublicationContentReportService {
  private final JdbcTemplate jdbc;
  private final CitizenPublicationRepository accounts;
  private final ObjectMapper json;
  private final Clock clock;

  PublicationContentReportService(
      JdbcTemplate jdbc, CitizenPublicationRepository accounts, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.accounts = accounts;
    this.json = json;
    this.clock = clock;
  }

  /** Recibe motivo (1–500 caracteres) e ID público; el denunciante procede solo de la sesión. */
  @Transactional
  void report(long id, String email, String reason) {
    String validReason = reason == null ? "" : reason.strip();
    if (validReason.isEmpty() || validReason.length() > 500)
      throw error(HttpStatus.BAD_REQUEST, "Indica un motivo de entre 1 y 500 caracteres");
    long reporter =
        accounts
            .lockAuthor(email)
            .orElseThrow(
                () -> error(HttpStatus.UNAUTHORIZED, "Inicia sesión para reportar contenido"));
    var publication =
        jdbc
            .query(
                "SELECT author_id, review_status FROM citizen_publication WHERE id = ? AND visible = TRUE FOR UPDATE",
                (row, index) ->
                    new Publication(row.getLong("author_id"), row.getString("review_status")),
                id)
            .stream()
            .findFirst()
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Publicación no disponible"));
    if (publication.authorId() == reporter)
      throw error(HttpStatus.FORBIDDEN, "El autor no puede denunciar su propia publicación");
    Integer existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_content_report WHERE publication_id = ? AND reporter_id = ? AND active_slot = 1",
            Integer.class,
            id,
            reporter);
    if (existing != null && existing > 0)
      throw error(HttpStatus.CONFLICT, "Ya tienes un reporte activo para esta publicación");
    var now = Timestamp.from(clock.instant());
    jdbc.update(
        "INSERT INTO publication_content_report (publication_id, reporter_id, reason, reported_at, active_slot) VALUES (?, ?, ?, ?, 1)",
        id,
        reporter,
        validReason,
        now);
    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT reporter_id) FROM publication_content_report WHERE publication_id = ? AND active_slot = 1 AND reporter_id <> ?",
            Integer.class,
            id,
            publication.authorId());
    if (total != null && total >= 3 && !"EN_REVISION".equals(publication.reviewStatus())) {
      jdbc.update("UPDATE citizen_publication SET review_status = 'EN_REVISION' WHERE id = ?", id);
      try {
        // Auditoría general visible al autor: solo el cambio de revisión, nunca el motivo privado.
        jdbc.update(
            "INSERT INTO citizen_publication_change (publication_id, actor_id, action, before_snapshot, after_snapshot, reason, changed_at) VALUES (?, ?, 'REVIEW', ?, ?, NULL, ?)",
            id,
            reporter,
            json.writeValueAsString(Map.of("reviewStatus", publication.reviewStatus())),
            json.writeValueAsString(Map.of("reviewStatus", "EN_REVISION")),
            now);
      } catch (JsonProcessingException failure) {
        throw new IllegalStateException("No se pudo auditar la revisión", failure);
      }
    }
  }

  private static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }

  private record Publication(long authorId, String reviewStatus) {}
}

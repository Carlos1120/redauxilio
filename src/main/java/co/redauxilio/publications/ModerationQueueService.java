package co.redauxilio.publications;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lecturas privadas y acotadas de moderación; no modifica publicación, denuncia, confianza ni
 * auditoría.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ModerationQueueService {
  private static final int CONTEXT_LIMIT = 50;
  private static final String SUMMARY =
      "SELECT "
          + CitizenPublicationRepository.PUBLIC_COLUMNS
          + ", visible, (SELECT COUNT(*) FROM publication_content_report r WHERE r.publication_id = citizen_publication.id AND r.active_slot = 1) AS active_reports"
          + ", (SELECT COUNT(*) FROM publication_content_report r WHERE r.publication_id = citizen_publication.id) AS total_reports"
          + " FROM citizen_publication";
  private final JdbcTemplate jdbc;

  ModerationQueueService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Cola EN_REVISION, incluidos ocultos/cerrados; página de 1–50 y offset 0–10000, orden estable
   * por ID.
   */
  Page list(int limit, int offset) {
    validatePage(limit, offset);
    List<CaseSummary> rows =
        jdbc.query(
            SUMMARY + " WHERE review_status = 'EN_REVISION' ORDER BY id ASC LIMIT ? OFFSET ?",
            (r, i) ->
                new CaseSummary(
                    CitizenPublicationRepository.mapPublic(r),
                    r.getBoolean("visible"),
                    r.getLong("active_reports"),
                    r.getLong("total_reports")),
            limit + 1,
            offset);
    return new Page(
        List.copyOf(rows.subList(0, Math.min(rows.size(), limit))),
        limit,
        offset,
        rows.size() > limit);
  }

  /**
   * Consulta por ID con denuncias, revisión u ocultamiento, incluso sin denuncias;
   * ejemplos/inexistentes dan 404.
   */
  CaseDetail detail(long id) {
    CaseSummary summary =
        jdbc
            .query(
                SUMMARY
                    + " WHERE id = ? AND (visible = FALSE OR review_status = 'EN_REVISION' OR EXISTS (SELECT 1 FROM publication_content_report r WHERE r.publication_id = citizen_publication.id))",
                (r, i) ->
                    new CaseSummary(
                        CitizenPublicationRepository.mapPublic(r),
                        r.getBoolean("visible"),
                        r.getLong("active_reports"),
                        r.getLong("total_reports")),
                id)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Caso de moderación no disponible"));
    List<Report> reports =
        jdbc.query(
            "SELECT id, reason, reported_at, active_slot FROM publication_content_report WHERE publication_id = ? ORDER BY reported_at DESC, id DESC LIMIT ?",
            (r, i) ->
                new Report(
                    r.getLong("id"),
                    r.getString("reason"),
                    r.getTimestamp("reported_at").toInstant().toString(),
                    r.getObject("active_slot") != null),
            id,
            CONTEXT_LIMIT + 1);
    List<Change> changes =
        jdbc.query(
            "SELECT actor_id, action, before_snapshot, after_snapshot, reason, changed_at FROM citizen_publication_change WHERE publication_id = ? ORDER BY changed_at DESC, id DESC LIMIT ?",
            (r, i) ->
                new Change(
                    r.getLong("actor_id"),
                    r.getString("action"),
                    r.getString("before_snapshot"),
                    r.getString("after_snapshot"),
                    r.getString("reason"),
                    r.getTimestamp("changed_at").toInstant().toString()),
            id,
            CONTEXT_LIMIT + 1);
    return new CaseDetail(
        summary,
        reports.stream().limit(CONTEXT_LIMIT).toList(),
        reports.size() > CONTEXT_LIMIT,
        changes.stream().limit(CONTEXT_LIMIT).toList(),
        changes.size() > CONTEXT_LIMIT);
  }

  /**
   * Auditoría interna de permisos: actor operativo, motivo y valores anterior/posterior, sin
   * contraseñas.
   */
  List<PermissionChange> permissions(int limit, int offset) {
    validatePage(limit, offset);
    return jdbc.query(
        "SELECT * FROM operator_permission_audit ORDER BY changed_at DESC, request_id DESC LIMIT ? OFFSET ?",
        (r, i) ->
            new PermissionChange(
                r.getString("request_id"),
                r.getLong("account_id"),
                r.getString("actor"),
                r.getString("action"),
                r.getBoolean("before_granted"),
                r.getBoolean("after_granted"),
                r.getString("reason"),
                r.getTimestamp("changed_at").toInstant().toString()),
        limit,
        offset);
  }

  private static void validatePage(int limit, int offset) {
    if (limit < 1 || limit > 50 || offset < 0 || offset > 10000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página fuera del rango permitido");
  }

  record CaseSummary(
      PublicationService.Publication publication,
      boolean visible,
      long activeReports,
      long totalReports) {}

  record Page(List<CaseSummary> items, int limit, int offset, boolean hasMore) {}

  // No se devuelve la identidad del denunciante: razón, fecha y vigencia bastan para este
  // incremento.
  record Report(long id, String reason, String reportedAt, boolean active) {}

  record Change(
      long actorId,
      String action,
      String beforeSnapshot,
      String afterSnapshot,
      String reason,
      String changedAt) {}

  record CaseDetail(
      CaseSummary context,
      List<Report> reports,
      boolean moreReports,
      List<Change> history,
      boolean moreHistory) {}

  record PermissionChange(
      String requestId,
      long accountId,
      String actor,
      String action,
      boolean beforeGranted,
      boolean afterGranted,
      String reason,
      String changedAt) {}
}

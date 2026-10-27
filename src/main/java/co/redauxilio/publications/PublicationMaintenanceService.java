package co.redauxilio.publications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Mantiene los reportes del autor con permisos en SQL, transiciones explícitas y auditoría atómica.
 * No permite alterar publicaciones ocultas o los ejemplos ficticios del mapa.
 */
@Service
class PublicationMaintenanceService {
  private static final Map<String, Set<String>> ASSISTANCE =
      Map.of(
          "DISPONIBLE", Set.of("CAPACIDAD LIMITADA", "LLENO", "CERRADO"),
          "CAPACIDAD LIMITADA", Set.of("DISPONIBLE", "LLENO", "CERRADO"),
          "LLENO", Set.of("DISPONIBLE", "CAPACIDAD LIMITADA", "CERRADO"),
          "CERRADO", Set.of("DISPONIBLE", "CAPACIDAD LIMITADA"));
  private static final Map<String, Set<String>> ROAD =
      Map.of(
          "BLOQUEADA", Set.of("PARCIALMENTE HABILITADA", "HABILITADA"),
          "PARCIALMENTE HABILITADA", Set.of("BLOQUEADA", "HABILITADA"),
          "HABILITADA", Set.of("BLOQUEADA", "PARCIALMENTE HABILITADA"));

  private final JdbcTemplate jdbc;
  private final PublicationService publications;
  private final ObjectMapper json;
  private final Clock clock;

  PublicationMaintenanceService(
      JdbcTemplate jdbc, PublicationService publications, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.publications = publications;
    this.json = json;
    this.clock = clock;
  }

  /** Solo enumera reportes persistidos del autor, nunca los seis ejemplos del demostrador. */
  @Transactional(readOnly = true)
  List<PublicationService.Publication> listOwn(String email) {
    List<Long> ids =
        jdbc.query(
            "SELECT p.id FROM citizen_publication p"
                + " JOIN citizen_account a ON a.id = p.author_id"
                + " WHERE a.email = ? AND p.visible = TRUE ORDER BY p.created_at DESC",
            (row, index) -> row.getLong(1),
            email);
    return ids.stream().map(publications::findPublication).toList();
  }

  /** Edita contenido y coordenadas, pero obliga a usar la transición para cambiar estados. */
  @Transactional
  PublicationService.Publication edit(long id, String email, CreatePublicationRequest request) {
    Owned before = owned(id, email, true);
    if (before.closed()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Reabre el caso antes de editar");
    }
    CreatePublicationRequest valid = PublicationCreationService.validate(request);
    if (!valid.category().equals(before.category())
        || !valid.operationalStatus().equals(before.operationalStatus())) {
      throw invalid("La categoría y el estado se cambian en flujos distintos");
    }
    Instant now = clock.instant();
    jdbc.update(
        "UPDATE citizen_publication SET assistance_subtype = ?, title = ?, description = ?,"
            + " location = ?, impact_type = ?, latitude = ?, longitude = ?, updated_at = ?"
            + " WHERE id = ?",
        valid.assistanceSubtype(),
        valid.title(),
        valid.description(),
        valid.location(),
        valid.impactType(),
        valid.latitude(),
        valid.longitude(),
        Timestamp.from(now),
        id);
    Owned after =
        new Owned(
            before.id(),
            before.authorId(),
            before.category(),
            valid.assistanceSubtype(),
            valid.title(),
            valid.description(),
            valid.location(),
            before.operationalStatus(),
            valid.impactType(),
            valid.latitude(),
            valid.longitude(),
            before.closed());
    audit(before, after, "EDIT", null, now);
    return publications.findPublication(id);
  }

  /** Rechaza transiciones no listadas, exige motivo de reapertura y preserva todo el historial. */
  @Transactional
  PublicationService.Publication transition(
      long id, String email, String desiredState, String reason) {
    Owned before = owned(id, email, true);
    if (desiredState == null || desiredState.isBlank()) throw invalid("Indica el estado nuevo");
    String target = desiredState.trim().toUpperCase(Locale.ROOT);
    Map<String, Set<String>> transitions =
        "ASSISTANCE".equals(before.category()) ? ASSISTANCE : ROAD;
    if (!transitions.getOrDefault(before.operationalStatus(), Set.of()).contains(target)) {
      throw invalid("Transición de estado no permitida");
    }
    boolean closing =
        "ASSISTANCE".equals(before.category())
            ? "CERRADO".equals(target)
            : "HABILITADA".equals(target);
    String explanation = reason == null ? "" : reason.trim();
    if (before.closed() && !closing && explanation.isBlank()) {
      throw invalid("La reapertura requiere un motivo");
    }
    if (explanation.length() > 500) throw invalid("El motivo supera 500 caracteres");
    Instant now = clock.instant();
    jdbc.update(
        "UPDATE citizen_publication SET operational_status = ?, closed = ?, updated_at = ?"
            + " WHERE id = ?",
        target,
        closing,
        Timestamp.from(now),
        id);
    Owned after =
        new Owned(
            before.id(),
            before.authorId(),
            before.category(),
            before.assistanceSubtype(),
            before.title(),
            before.description(),
            before.location(),
            target,
            before.impactType(),
            before.latitude(),
            before.longitude(),
            closing);
    String action = before.closed() ? "REOPEN" : closing ? "CLOSE" : "STATUS";
    audit(before, after, action, explanation.isBlank() ? null : explanation, now);
    return publications.findPublication(id);
  }

  /** El historial solo está disponible para el autor; incluye cambios previos y posteriores. */
  @Transactional(readOnly = true)
  List<Change> history(long id, String email) {
    owned(id, email, false);
    return jdbc.query(
        "SELECT action, before_snapshot, after_snapshot, reason, changed_at"
            + " FROM citizen_publication_change WHERE publication_id = ?"
            + " ORDER BY id ASC",
        (row, index) ->
            new Change(
                row.getString("action"),
                row.getString("before_snapshot"),
                row.getString("after_snapshot"),
                row.getString("reason"),
                row.getTimestamp("changed_at").toInstant().toString()),
        id);
  }

  private Owned owned(long id, String email, boolean lock) {
    String sql =
        "SELECT p.id, p.author_id, p.category, p.assistance_subtype, p.title,"
            + " p.description, p.location, p.operational_status, p.impact_type,"
            + " p.latitude, p.longitude, p.closed FROM citizen_publication p"
            + " WHERE p.id = ? AND p.visible = TRUE"
            + " AND p.author_id = (SELECT id FROM citizen_account WHERE email = ?)"
            + (lock ? " FOR UPDATE" : "");
    return jdbc
        .query(
            sql,
            (row, index) ->
                new Owned(
                    row.getLong("id"),
                    row.getLong("author_id"),
                    row.getString("category"),
                    row.getString("assistance_subtype"),
                    row.getString("title"),
                    row.getString("description"),
                    row.getString("location"),
                    row.getString("operational_status"),
                    row.getString("impact_type"),
                    row.getDouble("latitude"),
                    row.getDouble("longitude"),
                    row.getBoolean("closed")
                        || ("ROAD".equals(row.getString("category"))
                            && "HABILITADA".equals(row.getString("operational_status")))),
            id,
            email)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publicación no disponible"));
  }

  /** El cambio y su registro ocurren en la misma transacción: si falla uno, fallan ambos. */
  private void audit(Owned before, Owned after, String action, String reason, Instant when) {
    try {
      jdbc.update(
          "INSERT INTO citizen_publication_change"
              + " (publication_id, actor_id, action, before_snapshot, after_snapshot,"
              + " reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          before.id(),
          before.authorId(),
          action,
          json.writeValueAsString(before),
          json.writeValueAsString(after),
          reason,
          Timestamp.from(when));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("No fue posible registrar la auditoría", exception);
    }
  }

  private static ResponseStatusException invalid(String reason) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }

  record Change(
      String action,
      String beforeSnapshot,
      String afterSnapshot,
      String reason,
      String changedAt) {}

  private record Owned(
      long id,
      long authorId,
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String operationalStatus,
      String impactType,
      double latitude,
      double longitude,
      boolean closed) {}
}

package co.redauxilio.publications;

import co.redauxilio.identity.OperatorPermissions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Ejecuta decisiones RA-08 con bloqueo de publicación y auditoría privada en una transacción. */
@Service
class ModerationActionService {
  // La secuencia de publicaciones ciudadanas inicia aquí; los ejemplos fijos del mapa usan IDs
  // menores.
  private static final long FIRST_CITIZEN_PUBLICATION_ID = 1_000_000L;
  private static final Map<String, Set<String>> TRANSITIONS =
      Map.of(
          "ASSISTANCE:DISPONIBLE", Set.of("CAPACIDAD LIMITADA", "LLENO", "CERRADO"),
          "ASSISTANCE:CAPACIDAD LIMITADA", Set.of("DISPONIBLE", "LLENO", "CERRADO"),
          "ASSISTANCE:LLENO", Set.of("DISPONIBLE", "CAPACIDAD LIMITADA", "CERRADO"),
          "ASSISTANCE:CERRADO", Set.of("DISPONIBLE", "CAPACIDAD LIMITADA"),
          "ROAD:BLOQUEADA", Set.of("PARCIALMENTE HABILITADA", "HABILITADA"),
          "ROAD:PARCIALMENTE HABILITADA", Set.of("BLOQUEADA", "HABILITADA"),
          "ROAD:HABILITADA", Set.of("BLOQUEADA", "PARCIALMENTE HABILITADA"));

  private final JdbcTemplate jdbc;
  private final OperatorPermissions operators;
  private final ObjectMapper json;
  private final Clock clock;

  ModerationActionService(
      JdbcTemplate jdbc, OperatorPermissions operators, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.operators = operators;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Valida actor/motivo, bloquea el caso y aplica una acción explícita; cada petición es atómica.
   */
  @Transactional
  DecisionResult act(long id, String operatorEmail, ActionRequest request) {
    if (request == null) throw invalid("Faltan los datos de la decisión");
    String reason = request.reason() == null ? "" : request.reason().strip();
    if (reason.isEmpty() || reason.length() > 500)
      throw invalid("Indica un motivo de entre 1 y 500 caracteres");
    if (!operators.isOperator(operatorEmail))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permiso de operador no disponible");
    Long actor =
        jdbc
            .query(
                "SELECT id FROM citizen_account WHERE email = ?",
                (row, index) -> row.getLong(1),
                operatorEmail)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    Case before = lockCase(id);
    Instant now = clock.instant();
    Case after = before;
    String action =
        request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);
    switch (action) {
      case "KEEP" -> {
        if (!"EN_REVISION".equals(before.reviewStatus()))
          throw conflict("El caso ya no está en revisión");
        jdbc.update(
            "UPDATE publication_content_report SET active_slot = NULL WHERE publication_id = ? AND active_slot = 1",
            id);
        jdbc.update(
            "UPDATE citizen_publication SET review_status = 'SIN_REVISION' WHERE id = ?", id);
        after = before.withReviewStatus("SIN_REVISION").withActiveReports(0);
      }
      case "CORRECT" -> {
        if (before.closed()) throw conflict("Reabre el caso antes de corregirlo");
        CreatePublicationRequest valid = PublicationCreationService.validate(request.correction());
        if (!valid.category().equals(before.category())
            || !valid.operationalStatus().equals(before.status()))
          throw invalid("La corrección no puede cambiar categoría ni estado");
        after = before.corrected(valid);
        if (before.sameContent(after)) throw conflict("La corrección no cambia el contenido");
        jdbc.update(
            "UPDATE citizen_publication SET assistance_subtype = ?, title = ?, description = ?, location = ?, impact_type = ?, latitude = ?, longitude = ?, updated_at = ? WHERE id = ?",
            valid.assistanceSubtype(),
            valid.title(),
            valid.description(),
            valid.location(),
            valid.impactType(),
            valid.latitude(),
            valid.longitude(),
            Timestamp.from(now),
            id);
        invalidateConfirmations(id, actor, "Corrección sustancial de moderación");
      }
      case "HIDE" -> {
        if (!before.visible()) throw conflict("El caso ya está oculto");
        jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", id);
        after = before.withVisibility(false);
      }
      case "RESTORE" -> {
        if (before.visible()) throw conflict("El caso ya está visible");
        jdbc.update("UPDATE citizen_publication SET visible = TRUE WHERE id = ?", id);
        after = before.withVisibility(true);
      }
      case "CLOSE", "REOPEN" -> {
        String target =
            request.operationalStatus() == null
                ? ""
                : request.operationalStatus().strip().toUpperCase(Locale.ROOT);
        Set<String> allowed =
            TRANSITIONS.getOrDefault(before.category() + ":" + before.status(), Set.of());
        boolean closing = "CLOSE".equals(action);
        String terminal = "ASSISTANCE".equals(before.category()) ? "CERRADO" : "HABILITADA";
        if (closing && !terminal.equals(target))
          throw invalid("El cierre debe usar el estado terminal de la categoría");
        if (closing == before.closed() || !allowed.contains(target))
          throw conflict("Transición de estado no permitida");
        if (!closing && !before.closed()) throw conflict("El caso no está cerrado");
        jdbc.update(
            "UPDATE citizen_publication SET operational_status = ?, closed = ?, updated_at = ? WHERE id = ?",
            target,
            closing,
            Timestamp.from(now),
            id);
        after = before.withStatus(target, closing);
        invalidateConfirmations(
            id, actor, closing ? "Cierre de moderación" : "Reapertura de moderación");
      }
      default -> throw invalid("Decisión de moderación desconocida");
    }
    audit(id, actor, action, before, after, reason, now);
    return new DecisionResult(
        id, action, after.visible(), after.closed(), after.status(), after.reviewStatus());
  }

  private Case lockCase(long id) {
    return jdbc
        .query(
            "SELECT p.id, p.author_id, p.category, p.assistance_subtype, p.title, p.description, p.location, p.operational_status, p.impact_type, p.latitude, p.longitude, p.closed, p.visible, p.review_status, (SELECT COUNT(*) FROM publication_content_report r WHERE r.publication_id = p.id AND r.active_slot = 1) AS active_reports FROM citizen_publication p WHERE p.id = ? AND p.id >= "
                + FIRST_CITIZEN_PUBLICATION_ID
                + " FOR UPDATE",
            (row, index) ->
                new Case(
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
                            && "HABILITADA".equals(row.getString("operational_status"))),
                    row.getBoolean("visible"),
                    row.getString("review_status"),
                    row.getInt("active_reports")),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso no disponible"));
  }

  private void invalidateConfirmations(long id, long actor, String reason) {
    long version =
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id = ?", Long.class, id);
    jdbc.update(
        "UPDATE citizen_publication SET confirmation_version = confirmation_version + 1 WHERE id = ?",
        id);
    jdbc.update(
        "INSERT INTO publication_confirmation_invalidation (publication_id, actor_id, previous_version, next_version, reason, invalidated_at) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        actor,
        version,
        version + 1,
        reason,
        Timestamp.from(clock.instant()));
  }

  private void audit(
      long id, long actor, String action, Case before, Case after, String reason, Instant now) {
    try {
      jdbc.update(
          "INSERT INTO publication_moderation_action (publication_id, actor_id, action, before_snapshot, after_snapshot, reason, acted_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          id,
          actor,
          action,
          json.writeValueAsString(before),
          json.writeValueAsString(after),
          reason,
          Timestamp.from(now));
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("No se pudo registrar la decisión de moderación", failure);
    }
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  /** Entrada del operador: los campos auxiliares solo se usan en la acción que los requiere. */
  record ActionRequest(
      String action,
      String reason,
      String operationalStatus,
      CreatePublicationRequest correction) {}

  /** Respuesta acotada para no consultar la proyección pública de un caso que pudo ocultarse. */
  record DecisionResult(
      long publicationId,
      String action,
      boolean visible,
      boolean closed,
      String operationalStatus,
      String reviewStatus) {}

  private record Case(
      long id,
      long authorId,
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String status,
      String impactType,
      double latitude,
      double longitude,
      boolean closed,
      boolean visible,
      String reviewStatus,
      int activeReports) {
    Case withReviewStatus(String value) {
      return new Case(
          id,
          authorId,
          category,
          assistanceSubtype,
          title,
          description,
          location,
          status,
          impactType,
          latitude,
          longitude,
          closed,
          visible,
          value,
          activeReports);
    }

    Case withActiveReports(int value) {
      return new Case(
          id,
          authorId,
          category,
          assistanceSubtype,
          title,
          description,
          location,
          status,
          impactType,
          latitude,
          longitude,
          closed,
          visible,
          reviewStatus,
          value);
    }

    Case withVisibility(boolean value) {
      return new Case(
          id,
          authorId,
          category,
          assistanceSubtype,
          title,
          description,
          location,
          status,
          impactType,
          latitude,
          longitude,
          closed,
          value,
          reviewStatus,
          activeReports);
    }

    Case withStatus(String value, boolean isClosed) {
      return new Case(
          id,
          authorId,
          category,
          assistanceSubtype,
          title,
          description,
          location,
          value,
          impactType,
          latitude,
          longitude,
          isClosed,
          visible,
          reviewStatus,
          activeReports);
    }

    Case corrected(CreatePublicationRequest value) {
      return new Case(
          id,
          authorId,
          category,
          value.assistanceSubtype(),
          value.title(),
          value.description(),
          value.location(),
          status,
          value.impactType(),
          value.latitude(),
          value.longitude(),
          closed,
          visible,
          reviewStatus,
          activeReports);
    }

    boolean sameContent(Case other) {
      return java.util.Objects.equals(assistanceSubtype, other.assistanceSubtype)
          && title.equals(other.title)
          && description.equals(other.description)
          && location.equals(other.location)
          && java.util.Objects.equals(impactType, other.impactType)
          && Double.compare(latitude, other.latitude) == 0
          && Double.compare(longitude, other.longitude) == 0;
    }
  }
}

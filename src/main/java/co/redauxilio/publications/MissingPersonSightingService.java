package co.redauxilio.publications;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Registra observaciones append-only asociadas a casos visibles de persona desaparecida. */
@Service
class MissingPersonSightingService {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final co.redauxilio.identity.OperatorPermissions operators;
  static final int HOURLY_LIMIT = 5;
  static final int MAX_PAGE_SIZE = 20;

  MissingPersonSightingService(
      JdbcTemplate jdbc, Clock clock, co.redauxilio.identity.OperatorPermissions operators) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.operators = operators;
  }

  /** Devuelve el historial de un caso visible sin exponer identidad interna de sus autores. */
  @Transactional(readOnly = true)
  List<MissingPersonSighting> listPublic(long publicationId, int limit, int offset) {
    if (limit < 1 || limit > MAX_PAGE_SIZE || offset < 0)
      throw invalid("Pagina no valida (1-20 registros).");
    return readPage(publicationId, limit, offset, 0);
  }

  private List<MissingPersonSighting> readPage(
      long publicationId, int limit, int offset, long onlyId) {
    requireVisibleCase(publicationId);
    List<SightingRow> sightings =
        jdbc.query(
            "SELECT id, observed_at, location, latitude, longitude, description, created_at FROM missing_person_sighting WHERE publication_id = ? AND visible = TRUE AND (? = 0 OR id = ?) ORDER BY observed_at DESC, id DESC LIMIT ? OFFSET ?",
            (row, index) ->
                new SightingRow(
                    row.getLong(1),
                    row.getTimestamp(2).toInstant().toString(),
                    row.getString(3),
                    row.getDouble(4),
                    row.getDouble(5),
                    row.getString(6),
                    row.getTimestamp(7).toInstant().toString()),
            publicationId,
            onlyId,
            onlyId,
            limit,
            offset);
    if (sightings.isEmpty()) return List.of();

    String placeholders = String.join(",", java.util.Collections.nCopies(sightings.size(), "?"));
    Object[] ids = sightings.stream().map(SightingRow::id).toArray();
    Map<Long, List<PublicationEvidenceService.Evidence>> evidenceBySighting = new HashMap<>();
    jdbc.query(
        "SELECT id, sighting_id, content_type, added_at FROM missing_person_sighting_evidence WHERE sighting_id IN ("
            + placeholders
            + ") ORDER BY sighting_id, id",
        row -> {
          long sightingId = row.getLong(2);
          evidenceBySighting
              .computeIfAbsent(sightingId, ignored -> new ArrayList<>())
              .add(
                  new PublicationEvidenceService.Evidence(
                      row.getLong(1),
                      row.getString(3),
                      row.getTimestamp(4).toInstant().toString(),
                      evidenceUrl(publicationId, sightingId, row.getLong(1))));
        },
        ids);
    return sightings.stream()
        .map(
            sighting ->
                new MissingPersonSighting(
                    sighting.id(),
                    sighting.observedAt(),
                    sighting.location(),
                    sighting.latitude(),
                    sighting.longitude(),
                    sighting.description(),
                    sighting.createdAt(),
                    evidenceBySighting.getOrDefault(sighting.id(), List.of())))
        .toList();
  }

  /** Guarda observación e imágenes opcionales en una sola transacción, sin cambiar el caso. */
  @Transactional
  MissingPersonSighting create(
      long publicationId,
      String email,
      MissingPersonSightingRequest request,
      List<MultipartFile> files) {
    if (request == null) throw invalid("Faltan los datos del avistamiento.");
    MissingPersonSightingRequest validRequest = request.validate(clock);
    List<MultipartFile> uploads = files == null ? List.of() : files;
    if (uploads.size() > PublicationEvidenceService.MAX_IMAGES)
      throw invalid("Un avistamiento admite máximo tres imágenes.");
    List<PublicationEvidenceService.ValidatedImage> images =
        uploads.stream().map(PublicationEvidenceService::validate).toList();

    // El bloqueo de cuenta serializa cuotas incluso cuando los casos son distintos.
    long authorId = accountId(email);
    long recent =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM missing_person_sighting WHERE author_id=? AND created_at > ?",
            Long.class,
            authorId,
            Timestamp.from(clock.instant().minusSeconds(3600)));
    if (recent >= HOURLY_LIMIT)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Limite de cinco avistamientos por hora. Intenta mas tarde.");
    requireActiveCaseForUpdate(publicationId);
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO missing_person_sighting (publication_id, author_id, observed_at, location, latitude, longitude, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          statement.setLong(1, publicationId);
          statement.setLong(2, authorId);
          statement.setTimestamp(
              3, Timestamp.from(java.time.Instant.parse(validRequest.observedAt())));
          statement.setString(4, validRequest.location());
          statement.setDouble(5, validRequest.latitude());
          statement.setDouble(6, validRequest.longitude());
          statement.setString(7, validRequest.description());
          statement.setTimestamp(8, Timestamp.from(clock.instant()));
          return statement;
        },
        keys);
    Number generatedId = keys.getKey();
    if (generatedId == null)
      throw new IllegalStateException("No se generó el ID del avistamiento.");
    long sightingId = generatedId.longValue();
    for (PublicationEvidenceService.ValidatedImage image : images)
      jdbc.update(
          "INSERT INTO missing_person_sighting_evidence (sighting_id, content_type, image_bytes, added_at) VALUES (?, ?, ?, ?)",
          sightingId,
          image.contentType(),
          image.bytes(),
          Timestamp.from(clock.instant()));
    return readPage(publicationId, 1, 0, sightingId).stream()
        .filter(sighting -> sighting.id() == sightingId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No se pudo leer el avistamiento creado."));
  }

  /** Sirve bytes solo si el caso padre y el avistamiento siguen siendo públicos. */
  @Transactional(readOnly = true)
  PublicationEvidenceService.Image image(long publicationId, long sightingId, long evidenceId) {
    return jdbc
        .query(
            "SELECT e.content_type, e.image_bytes FROM missing_person_sighting_evidence e JOIN missing_person_sighting s ON s.id = e.sighting_id JOIN citizen_publication p ON p.id = s.publication_id WHERE s.visible = TRUE AND p.visible = TRUE AND p.category = 'MISSING_PERSON' AND p.id = ? AND s.id = ? AND e.id = ?",
            (row, index) -> new PublicationEvidenceService.Image(row.getString(1), row.getBytes(2)),
            publicationId,
            sightingId,
            evidenceId)
        .stream()
        .findFirst()
        .orElseThrow(MissingPersonSightingService::notFound);
  }

  /** Oculta solo el anexo y conserva sus datos; auditoria y visibilidad se confirman juntas. */
  @Transactional
  void hide(long sightingId, String email, String reason) {
    if (!operators.isOperator(email)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (sightingId <= 0 || reason == null || reason.isBlank() || reason.strip().length() > 500)
      throw invalid("Indica un ID positivo y un motivo de 1 a 500 caracteres.");
    long actorId = accountId(email);
    var visible =
        jdbc.query(
            "SELECT visible FROM missing_person_sighting WHERE id=? FOR UPDATE",
            (row, index) -> row.getBoolean(1),
            sightingId);
    if (visible.isEmpty()) throw notFound();
    if (!visible.get(0))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "El avistamiento ya esta oculto.");
    jdbc.update("UPDATE missing_person_sighting SET visible=FALSE WHERE id=?", sightingId);
    jdbc.update(
        "INSERT INTO missing_person_sighting_moderation (sighting_id,actor_id,reason,changed_at) VALUES (?,?,?,?)",
        sightingId,
        actorId,
        reason.strip(),
        Timestamp.from(clock.instant()));
  }

  /** Historial privado acotado; preserva actor, motivo y transicion visible a oculto. */
  @Transactional(readOnly = true)
  List<ModerationEvent> moderationHistory(long sightingId, String email) {
    if (!operators.isOperator(email)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return jdbc.query(
        "SELECT actor_id,reason,changed_at FROM missing_person_sighting_moderation WHERE sighting_id=? ORDER BY id DESC LIMIT 20",
        (row, index) ->
            new ModerationEvent(
                row.getLong(1),
                row.getString(2),
                row.getTimestamp(3).toInstant().toString(),
                "HIDE",
                true,
                false),
        sightingId);
  }

  record ModerationEvent(
      long actorId,
      String reason,
      String changedAt,
      String action,
      boolean beforeVisible,
      boolean afterVisible) {}

  private void requireVisibleCase(long publicationId) {
    if (jdbc.query(
            "SELECT id FROM citizen_publication WHERE id = ? AND category = 'MISSING_PERSON' AND visible = TRUE",
            (row, index) -> row.getLong(1),
            publicationId)
        .isEmpty()) throw notFound();
  }

  private void requireActiveCaseForUpdate(long publicationId) {
    var cases =
        jdbc.query(
            "SELECT operational_status, closed FROM citizen_publication WHERE id = ? AND category = 'MISSING_PERSON' AND visible = TRUE FOR UPDATE",
            (row, index) -> new CaseState(row.getString(1), row.getBoolean(2)),
            publicationId);
    if (cases.isEmpty()) throw notFound();
    CaseState target = cases.get(0);
    if (target.closed() || !"DESAPARECIDA".equals(target.operationalStatus()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Solo se pueden registrar avistamientos en un caso activo.");
  }

  private long accountId(String email) {
    List<Long> ids =
        jdbc.query(
            "SELECT id FROM citizen_account WHERE email = ? FOR UPDATE",
            (row, index) -> row.getLong(1),
            email);
    if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return ids.get(0);
  }

  private static String evidenceUrl(long publicationId, long sightingId, long evidenceId) {
    return "/api/publications/"
        + publicationId
        + "/sightings/"
        + sightingId
        + "/evidence/"
        + evidenceId;
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso o avistamiento no disponible.");
  }

  private record SightingRow(
      long id,
      String observedAt,
      String location,
      double latitude,
      double longitude,
      String description,
      String createdAt) {}

  private record CaseState(String operationalStatus, boolean closed) {}
}

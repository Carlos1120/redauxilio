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

  MissingPersonSightingService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Devuelve el historial de un caso visible sin exponer identidad interna de sus autores. */
  @Transactional(readOnly = true)
  List<MissingPersonSighting> listPublic(long publicationId) {
    requireVisibleCase(publicationId);
    List<SightingRow> sightings =
        jdbc.query(
            "SELECT id, observed_at, location, latitude, longitude, description, created_at FROM missing_person_sighting WHERE publication_id = ? ORDER BY observed_at DESC, id DESC",
            (row, index) ->
                new SightingRow(
                    row.getLong(1),
                    row.getTimestamp(2).toInstant().toString(),
                    row.getString(3),
                    row.getDouble(4),
                    row.getDouble(5),
                    row.getString(6),
                    row.getTimestamp(7).toInstant().toString()),
            publicationId);
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

    requireActiveCaseForUpdate(publicationId);
    long authorId = accountId(email);
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
    return listPublic(publicationId).stream()
        .filter(sighting -> sighting.id() == sightingId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No se pudo leer el avistamiento creado."));
  }

  /** Sirve bytes solo si el caso padre y el avistamiento siguen siendo públicos. */
  @Transactional(readOnly = true)
  PublicationEvidenceService.Image image(long publicationId, long sightingId, long evidenceId) {
    return jdbc
        .query(
            "SELECT e.content_type, e.image_bytes FROM missing_person_sighting_evidence e JOIN missing_person_sighting s ON s.id = e.sighting_id JOIN citizen_publication p ON p.id = s.publication_id WHERE p.visible = TRUE AND p.category = 'MISSING_PERSON' AND p.id = ? AND s.id = ? AND e.id = ?",
            (row, index) -> new PublicationEvidenceService.Image(row.getString(1), row.getBytes(2)),
            publicationId,
            sightingId,
            evidenceId)
        .stream()
        .findFirst()
        .orElseThrow(MissingPersonSightingService::notFound);
  }

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
            "SELECT id FROM citizen_account WHERE email = ?",
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

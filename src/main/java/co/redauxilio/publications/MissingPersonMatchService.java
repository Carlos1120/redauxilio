package co.redauxilio.publications;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Ejecuta una coincidencia determinista preparada para la exposición. */
@Service
class MissingPersonMatchService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  MissingPersonMatchService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Lista únicamente candidatos activos con al menos una foto preparada. */
  @Transactional(readOnly = true)
  List<Candidate> candidates() {
    return jdbc.query(
        "SELECT c.id,c.reference_name,c.age_range,c.description,c.match_score,COUNT(p.id) "
            + "FROM simulated_missing_candidate c JOIN simulated_missing_photo p ON p.candidate_id=c.id "
            + "WHERE c.active=TRUE GROUP BY c.id,c.reference_name,c.age_range,c.description,c.match_score "
            + "ORDER BY c.id",
        (row, index) ->
            new Candidate(
                row.getLong(1),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getInt(5),
                row.getInt(6),
                "/api/missing-person-match/candidates/" + row.getLong(1) + "/photo",
                "/api/missing-person-match/candidates/" + row.getLong(1) + "/comparison-photo"));
  }

  /** Compara de una a tres fotos en memoria; nunca persiste las imágenes enviadas. */
  @Transactional(readOnly = true)
  MatchResult simulate(List<MultipartFile> files) {
    if (files == null || files.isEmpty() || files.size() > PublicationEvidenceService.MAX_IMAGES)
      throw invalid("Adjunta entre una y tres imágenes para ejecutar la comparación.");
    List<String> hashes =
        files.stream()
            .map(PublicationEvidenceService::validate)
            .map(image -> sha256(image.bytes()))
            .toList();
    String placeholders = String.join(",", java.util.Collections.nCopies(hashes.size(), "?"));
    List<Candidate> matches =
        jdbc.query(
            "SELECT DISTINCT c.id,c.reference_name,c.age_range,c.description,c.match_score,"
                + "(SELECT COUNT(*) FROM simulated_missing_photo all_photos WHERE all_photos.candidate_id=c.id) "
                + "FROM simulated_missing_candidate c JOIN simulated_missing_match_input p ON p.candidate_id=c.id "
                + "WHERE c.active=TRUE AND p.image_sha256 IN ("
                + placeholders
                + ") ORDER BY c.match_score DESC,c.id LIMIT 1",
            (row, index) ->
                new Candidate(
                    row.getLong(1),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getInt(5),
                    row.getInt(6),
                    "/api/missing-person-match/candidates/" + row.getLong(1) + "/photo",
                    "/api/missing-person-match/candidates/" + row.getLong(1) + "/comparison-photo"),
            hashes.toArray());
    if (matches.isEmpty())
      return new MatchResult(
          false,
          null,
          files.size(),
          "No se encontró una coincidencia dentro de los casos preparados.");
    Candidate candidate = matches.get(0);
    return new MatchResult(
        true,
        candidate,
        files.size(),
        "Resultado predispuesto para demostrar un flujo futuro; no confirma identidad.");
  }

  /** Sirve la primera foto de referencia sin permitir caché ni exponer nombres de archivo. */
  @Transactional(readOnly = true)
  PublicationEvidenceService.Image photo(long candidateId) {
    return jdbc
        .query(
            "SELECT p.content_type,p.image_bytes FROM simulated_missing_photo p "
                + "JOIN simulated_missing_candidate c ON c.id=p.candidate_id "
                + "WHERE c.id=? AND c.active=TRUE ORDER BY p.id LIMIT 1",
            (row, index) -> new PublicationEvidenceService.Image(row.getString(1), row.getBytes(2)),
            candidateId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Foto de referencia no disponible."));
  }

  /** Sirve la entrada controlada para que la exposición pueda repetir la comparación. */
  @Transactional(readOnly = true)
  PublicationEvidenceService.Image comparisonPhoto(long candidateId) {
    return jdbc
        .query(
            "SELECT p.content_type,p.image_bytes FROM simulated_missing_match_input p "
                + "JOIN simulated_missing_candidate c ON c.id=p.candidate_id "
                + "WHERE c.id=? AND c.active=TRUE ORDER BY p.id LIMIT 1",
            (row, index) -> new PublicationEvidenceService.Image(row.getString(1), row.getBytes(2)),
            candidateId)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Foto de comparación no disponible."));
  }

  /** Inserta o actualiza un caso desde el manifiesto local autorizado. */
  @Transactional
  long upsertFixture(
      Fixture fixture,
      List<PublicationEvidenceService.ValidatedImage> images,
      List<PublicationEvidenceService.ValidatedImage> comparisonImages) {
    List<Long> existing =
        jdbc.query(
            "SELECT id FROM simulated_missing_candidate WHERE code=?",
            (row, index) -> row.getLong(1),
            fixture.code());
    long id;
    if (existing.isEmpty()) {
      var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
      jdbc.update(
          connection -> {
            var statement =
                connection.prepareStatement(
                    "INSERT INTO simulated_missing_candidate(code,reference_name,age_range,description,match_score,active,created_at) VALUES (?,?,?,?,?,TRUE,?)",
                    new String[] {"id"});
            statement.setString(1, fixture.code());
            statement.setString(2, fixture.referenceName());
            statement.setString(3, fixture.ageRange());
            statement.setString(4, fixture.description());
            statement.setInt(5, fixture.score());
            statement.setTimestamp(6, Timestamp.from(clock.instant()));
            return statement;
          },
          keys);
      Number generated = keys.getKey();
      if (generated == null)
        throw new IllegalStateException("No se generó el ID del caso preparado.");
      id = generated.longValue();
    } else {
      id = existing.get(0);
      jdbc.update(
          "UPDATE simulated_missing_candidate SET reference_name=?,age_range=?,description=?,match_score=?,active=TRUE WHERE id=?",
          fixture.referenceName(),
          fixture.ageRange(),
          fixture.description(),
          fixture.score(),
          id);
    }
    jdbc.update("DELETE FROM simulated_missing_match_input WHERE candidate_id=?", id);
    jdbc.update("DELETE FROM simulated_missing_photo WHERE candidate_id=?", id);
    for (PublicationEvidenceService.ValidatedImage image : images) {
      String hash = sha256(image.bytes());
      jdbc.update(
          "INSERT INTO simulated_missing_photo(candidate_id,content_type,image_bytes,image_sha256,created_at) VALUES (?,?,?,?,?)",
          id,
          image.contentType(),
          image.bytes(),
          hash,
          Timestamp.from(clock.instant()));
    }
    for (PublicationEvidenceService.ValidatedImage image : comparisonImages) {
      jdbc.update(
          "INSERT INTO simulated_missing_match_input(candidate_id,content_type,image_bytes,image_sha256,created_at) VALUES (?,?,?,?,?)",
          id,
          image.contentType(),
          image.bytes(),
          sha256(image.bytes()),
          Timestamp.from(clock.instant()));
    }
    return id;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 no está disponible.", impossible);
    }
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  record Candidate(
      long id,
      String referenceName,
      String ageRange,
      String description,
      int simulatedScore,
      int photoCount,
      String photoUrl,
      String comparisonPhotoUrl) {}

  record MatchResult(boolean matched, Candidate candidate, int comparedPhotos, String message) {}

  record Fixture(
      String code,
      String referenceName,
      String ageRange,
      String description,
      int score,
      List<String> images,
      List<String> comparisonImages) {}
}

package co.redauxilio.publications;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Advierte coincidencias publicas sin revelar autores/contactos ni fusionar casos. */
@Service
class DuplicatePublicationService {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  DuplicatePublicationService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Serializa solo altas de la misma categoria hasta commit/rollback, tambien entre autores. */
  void lockCategory(String category) {
    jdbc.queryForObject(
        "SELECT category FROM publication_duplicate_guard WHERE category=? FOR UPDATE",
        String.class,
        category);
  }

  /** Lee candidatos por bloques acotados; no materializa la base completa ni fotografias. */
  @Transactional(readOnly = true)
  List<Candidate> find(CreatePublicationRequest input) {
    var request = PublicationCreationService.validate(input, clock);
    boolean missing = "MISSING_PERSON".equals(request.category());
    List<Candidate> matches = new ArrayList<>();
    long cursor = 0;
    while (matches.size() < 20) {
      String condition =
          missing
              ? " AND closed=FALSE AND operational_status <> 'CASO CERRADO'"
              : " AND created_at >= ? AND created_at <= ? AND latitude BETWEEN ? AND ?";
      List<Object> params = new ArrayList<>(List.of(request.category(), cursor));
      if (!missing) {
        params.add(Timestamp.from(clock.instant().minusSeconds(86400)));
        params.add(Timestamp.from(clock.instant()));
        double delta = Math.toDegrees(250d / 6371000d);
        params.add(request.latitude() - delta);
        params.add(request.latitude() + delta);
      }
      var rows =
          jdbc.query(
              "SELECT id,title,location,latitude,longitude,reference_name,age_range,physical_description,last_seen_place,last_seen_at FROM citizen_publication WHERE visible=TRUE AND category=? AND id>?"
                  + condition
                  + " ORDER BY id LIMIT 200",
              (r, i) ->
                  new Row(
                      r.getLong(1),
                      r.getString(2),
                      r.getString(3),
                      r.getDouble(4),
                      r.getDouble(5),
                      new MissingPersonDetails(
                          r.getString(6),
                          r.getString(7),
                          r.getString(8),
                          r.getString(9),
                          r.getTimestamp(10) == null
                              ? null
                              : r.getTimestamp(10).toInstant().toString())),
              params.toArray());
      for (var row : rows) {
        cursor = row.id();
        if (missing
            ? DuplicatePolicy.matchesPerson(request.missingPerson(), row.person())
            : DuplicatePolicy.distance(
                    request.latitude(), request.longitude(), row.latitude(), row.longitude())
                <= 250d + 0.0000001) {
          matches.add(
              new Candidate(
                  row.id(),
                  row.title(),
                  row.location(),
                  missing ? row.person().lastSeenAt() : null));
          if (matches.size() == 20) break;
        }
      }
      if (rows.size() < 200) break;
    }
    return matches;
  }

  /** La comprobacion final se realiza dentro de la transaccion que guarda campos y fotos. */
  void requireJustification(CreatePublicationRequest input) {
    String reason = input.duplicateJustification();
    if (reason != null && !reason.isBlank()) {
      if (reason.strip().length() > 500)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Justificacion de maximo 500 caracteres");
      MissingPersonDetails.validateReason(reason);
    }
    if (!find(input).isEmpty() && (reason == null || reason.isBlank()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Hay posibles duplicados. Consulta los casos y aporta una justificacion para continuar.");
  }

  void record(long id, String reason) {
    if (reason != null && !reason.isBlank())
      jdbc.update(
          "INSERT INTO publication_duplicate_justification(publication_id,reason,created_at) VALUES (?,?,?)",
          id,
          reason.strip(),
          Timestamp.from(clock.instant()));
  }

  record Candidate(long id, String title, String location, String lastSeenAt) {}

  private record Row(
      long id,
      String title,
      String location,
      double latitude,
      double longitude,
      MissingPersonDetails person) {}
}

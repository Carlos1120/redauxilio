package co.redauxilio.publications;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Persiste reportes ciudadanos y mantiene la autoría fuera del JSON público. */
@Repository
class CitizenPublicationRepository {
  private final JdbcTemplate jdbc;

  CitizenPublicationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Bloquea la fila de cuenta dentro de la transacción para serializar el cupo por autor. */
  Optional<Long> lockAuthor(String email) {
    return jdbc
        .query(
            "SELECT id FROM citizen_account WHERE email = ? FOR UPDATE",
            (row, index) -> row.getLong(1),
            email)
        .stream()
        .findFirst();
  }

  long countSince(long authorId, Instant cutoff) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication WHERE author_id = ? AND created_at > ?",
            Long.class,
            authorId,
            Timestamp.from(cutoff));
    return count == null ? 0 : count;
  }

  /** Inserta una fila completa y devuelve su identificador; el servicio controla la transacción. */
  long insert(long authorId, CreatePublicationRequest request, Instant now) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO citizen_publication"
                      + " (author_id, category, assistance_subtype, title, description, location,"
                      + " operational_status, impact_type, latitude, longitude, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                  Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, authorId);
          statement.setString(2, request.category());
          statement.setString(3, request.assistanceSubtype());
          statement.setString(4, request.title());
          statement.setString(5, request.description());
          statement.setString(6, request.location());
          statement.setString(7, request.operationalStatus());
          statement.setString(8, request.impactType());
          statement.setDouble(9, request.latitude());
          statement.setDouble(10, request.longitude());
          statement.setTimestamp(11, Timestamp.from(now));
          statement.setTimestamp(12, Timestamp.from(now));
          return statement;
        },
        keys);
    Number id = keys.getKey();
    if (id == null) throw new IllegalStateException("La publicación no obtuvo identificador.");
    return id.longValue();
  }

  /** Entrega solo contenido visible, sin ID/correo del autor ni columnas internas. */
  List<PublicationService.Publication> findVisible() {
    return jdbc.query(
        "SELECT id, category, assistance_subtype, title, description, location, operational_status,"
            + " impact_type, latitude, longitude, created_at, updated_at, closed"
            + " FROM citizen_publication WHERE visible = TRUE",
        (row, index) ->
            new PublicationService.Publication(
                row.getLong("id"),
                row.getString("category"),
                row.getString("title"),
                row.getString("location"),
                row.getString("operational_status"),
                "REPORTADA",
                row.getTimestamp("updated_at").toInstant().toString(),
                row.getDouble("latitude"),
                row.getDouble("longitude"),
                row.getString("description"),
                row.getTimestamp("created_at").toInstant().toString(),
                row.getBoolean("closed"),
                row.getString("assistance_subtype"),
                row.getString("impact_type")));
  }
}

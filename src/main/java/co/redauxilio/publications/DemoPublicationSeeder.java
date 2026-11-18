package co.redauxilio.publications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga reportes visuales de la exposición en SQL. Cada título es estable para actualizar la misma
 * fila al reiniciar; la cuenta técnica recibe una contraseña aleatoria que nunca se publica.
 */
@Component
@ConditionalOnProperty(
    name = "redauxilio.demo.reports-enabled",
    havingValue = "true",
    matchIfMissing = true)
class DemoPublicationSeeder implements ApplicationRunner {
  private static final String AUTHOR_EMAIL = "muestras@redauxilio.local";
  private final JdbcTemplate jdbc;
  private final CitizenPublicationRepository publications;
  private final PasswordEncoder passwords;
  private final ObjectMapper json;
  private final ResourceLoader resources;
  private final Clock clock;
  private final String manifestLocation;

  DemoPublicationSeeder(
      JdbcTemplate jdbc,
      CitizenPublicationRepository publications,
      PasswordEncoder passwords,
      ObjectMapper json,
      ResourceLoader resources,
      Clock clock,
      @Value("${redauxilio.demo.reports-manifest:classpath:demo-publications/reports.json}")
          String manifestLocation) {
    this.jdbc = jdbc;
    this.publications = publications;
    this.passwords = passwords;
    this.json = json;
    this.resources = resources;
    this.clock = clock;
    this.manifestLocation = manifestLocation;
  }

  /** Inserta o actualiza el lote completo dentro de una sola transacción. */
  @Override
  @Transactional
  public void run(ApplicationArguments arguments) throws Exception {
    Resource manifest = resources.getResource(manifestLocation);
    if (!manifest.exists())
      throw new IllegalStateException("Falta el catálogo de reportes iniciales.");
    List<Fixture> fixtures =
        json.readValue(manifest.getInputStream(), new TypeReference<List<Fixture>>() {});
    if (fixtures.isEmpty()) throw new IllegalStateException("El catálogo de reportes está vacío.");
    long authorId = findOrCreateAuthor();
    for (Fixture fixture : fixtures) seed(authorId, fixture);
  }

  private void seed(long authorId, Fixture fixture) throws Exception {
    if (fixture.code() == null
        || fixture.code().isBlank()
        || fixture.images() == null
        || fixture.images().isEmpty()
        || fixture.images().size() > PublicationEvidenceService.MAX_IMAGES)
      throw new IllegalStateException("Reporte inválido en el catálogo inicial.");
    CreatePublicationRequest request =
        PublicationCreationService.validate(fixture.request(), clock);
    List<PublicationEvidenceService.ValidatedImage> images = new ArrayList<>();
    for (String location : fixture.images()) {
      Resource image = resources.getResource(location);
      if (!image.exists())
        throw new IllegalStateException("Falta una imagen del catálogo inicial.");
      images.add(
          PublicationEvidenceService.validate(
              image.getFilename(),
              contentType(image.getFilename()),
              image.getContentAsByteArray()));
    }
    long publicationId = upsertPublication(authorId, request);
    jdbc.update("DELETE FROM publication_evidence WHERE publication_id = ?", publicationId);
    Instant now = clock.instant();
    for (PublicationEvidenceService.ValidatedImage image : images)
      jdbc.update(
          "INSERT INTO publication_evidence(publication_id,content_type,image_bytes,added_at) VALUES (?,?,?,?)",
          publicationId,
          image.contentType(),
          image.bytes(),
          Timestamp.from(now));
  }

  private long upsertPublication(long authorId, CreatePublicationRequest request) {
    List<Long> existing =
        jdbc.query(
            "SELECT id FROM citizen_publication WHERE author_id = ? AND title = ?",
            (row, index) -> row.getLong(1),
            authorId,
            request.title());
    if (existing.isEmpty()) return publications.insert(authorId, request, clock.instant());
    long id = existing.get(0);
    jdbc.update(
        "UPDATE citizen_publication SET category=?,assistance_subtype=?,description=?,location=?,operational_status=?,impact_type=?,latitude=?,longitude=?,updated_at=?,visible=TRUE,closed=FALSE WHERE id=?",
        request.category(),
        request.assistanceSubtype(),
        request.description(),
        request.location(),
        request.operationalStatus(),
        request.impactType(),
        request.latitude(),
        request.longitude(),
        Timestamp.from(clock.instant()),
        id);
    return id;
  }

  private long findOrCreateAuthor() {
    List<Long> existing =
        jdbc.query(
            "SELECT id FROM citizen_account WHERE email = ?",
            (row, index) -> row.getLong(1),
            AUTHOR_EMAIL);
    if (!existing.isEmpty()) return existing.get(0);
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  "INSERT INTO citizen_account(email,display_name,password_hash,created_at) VALUES (?,?,?,?)",
                  new String[] {"id"});
          statement.setString(1, AUTHOR_EMAIL);
          statement.setString(2, "Información comunitaria");
          statement.setString(3, passwords.encode(UUID.randomUUID().toString()));
          statement.setTimestamp(4, Timestamp.from(clock.instant()));
          return statement;
        },
        keys);
    Number id = keys.getKey();
    if (id == null) throw new IllegalStateException("La cuenta técnica no obtuvo identificador.");
    return id.longValue();
  }

  private static String contentType(String filename) {
    if (filename == null) throw new IllegalStateException("Imagen sin nombre.");
    String normalized = filename.toLowerCase(java.util.Locale.ROOT);
    if (normalized.endsWith(".png")) return "image/png";
    if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
    if (normalized.endsWith(".webp")) return "image/webp";
    throw new IllegalStateException("Formato de imagen no admitido.");
  }

  record Fixture(
      String code,
      String category,
      String assistanceSubtype,
      String title,
      String description,
      String location,
      String operationalStatus,
      String impactType,
      Double latitude,
      Double longitude,
      List<String> images) {
    CreatePublicationRequest request() {
      return new CreatePublicationRequest(
          category,
          assistanceSubtype,
          title,
          description,
          location,
          operationalStatus,
          impactType,
          latitude,
          longitude);
    }
  }
}

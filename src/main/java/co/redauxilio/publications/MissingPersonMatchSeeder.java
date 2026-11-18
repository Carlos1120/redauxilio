package co.redauxilio.publications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Carga casos preparados solo desde el manifiesto local configurado por el equipo. */
@Component
class MissingPersonMatchSeeder implements ApplicationRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(MissingPersonMatchSeeder.class);
  private final MissingPersonMatchService service;
  private final ObjectMapper json;
  private final ResourceLoader resources;
  private final String manifestLocation;

  MissingPersonMatchSeeder(
      MissingPersonMatchService service,
      ObjectMapper json,
      ResourceLoader resources,
      @Value("${redauxilio.simulation.match-manifest:classpath:demo-match/candidates.json}")
          String manifestLocation) {
    this.service = service;
    this.json = json;
    this.resources = resources;
    this.manifestLocation = manifestLocation;
  }

  /** Un manifiesto vacío deja la interfaz preparada sin introducir rostros en el repositorio. */
  @Override
  public void run(ApplicationArguments arguments) throws IOException {
    Resource manifest = resources.getResource(manifestLocation);
    if (!manifest.exists()) {
      LOGGER.info("No hay manifiesto de coincidencias; el catálogo queda vacío.");
      return;
    }
    List<MissingPersonMatchService.Fixture> fixtures =
        json.readValue(manifest.getInputStream(), new TypeReference<>() {});
    for (MissingPersonMatchService.Fixture fixture : fixtures) {
      validate(fixture);
      List<PublicationEvidenceService.ValidatedImage> images = new ArrayList<>();
      for (String location : fixture.images()) {
        Resource image = resources.getResource(location);
        if (!image.exists())
          throw new IllegalStateException("Falta una foto declarada en el manifiesto.");
        images.add(
            PublicationEvidenceService.validate(
                image.getFilename(),
                contentType(image.getFilename()),
                image.getContentAsByteArray()));
      }
      List<PublicationEvidenceService.ValidatedImage> comparisonImages = new ArrayList<>();
      for (String location : fixture.comparisonImages()) {
        Resource image = resources.getResource(location);
        if (!image.exists())
          throw new IllegalStateException("Falta la foto de comparación declarada.");
        comparisonImages.add(
            PublicationEvidenceService.validate(
                image.getFilename(),
                contentType(image.getFilename()),
                image.getContentAsByteArray()));
      }
      service.upsertFixture(fixture, images, comparisonImages);
    }
  }

  private static void validate(MissingPersonMatchService.Fixture fixture) {
    if (fixture.code() == null
        || fixture.code().isBlank()
        || fixture.referenceName() == null
        || fixture.referenceName().isBlank()
        || fixture.description() == null
        || fixture.description().isBlank()
        || fixture.score() < 1
        || fixture.score() > 99
        || fixture.images() == null
        || fixture.images().isEmpty()
        || fixture.images().size() > PublicationEvidenceService.MAX_IMAGES
        || fixture.comparisonImages() == null
        || fixture.comparisonImages().isEmpty()
        || fixture.comparisonImages().size() > PublicationEvidenceService.MAX_IMAGES)
      throw new IllegalStateException("Caso inválido en el manifiesto.");
  }

  private static String contentType(String filename) {
    String lower = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    throw new IllegalStateException("La foto del manifiesto debe ser JPEG, PNG o WebP.");
  }
}

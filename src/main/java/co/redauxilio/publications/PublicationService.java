package co.redauxilio.publications;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Consulta sintética; controla visibilidad y cierre antes de devolver datos públicos. */
@Service
public class PublicationService {
  private static final Set<String> CATEGORIES =
      Set.of("ASSISTANCE", "ROAD", "MISSING_PERSON", "HELP_REQUEST");
  private static final Set<String> STATUSES =
      Set.of(
          "DISPONIBLE",
          "CAPACIDAD LIMITADA",
          "LLENO",
          "CERRADO",
          "BLOQUEADA",
          "PARCIALMENTE HABILITADA",
          "HABILITADA",
          "SIN LOCALIZAR",
          "ENCONTRADA",
          "ABIERTA",
          "EN ATENCIÓN",
          "SATISFECHA");
  private static final Set<String> CONFIDENCE =
      Set.of("REPORTADA", "CONFIRMADA", "VERIFICADA", "EN_REVISION", "DESACTUALIZADA");
  private final List<Entry> publications;

  public PublicationService() {
    this(
        List.of(
            new Entry(
                sample(
                    1,
                    "ASSISTANCE",
                    "Refugio de demostración",
                    "Villavicencio · ubicación ficticia",
                    "Disponible",
                    4.15,
                    -73.63,
                    false),
                true),
            new Entry(
                sample(
                    2,
                    "ROAD",
                    "Vía afectada de demostración",
                    "Tramo ficticio",
                    "Bloqueada",
                    4.14,
                    -73.62,
                    false),
                true),
            new Entry(
                sample(
                    3,
                    "MISSING_PERSON",
                    "Caso ficticio de persona desaparecida",
                    "Datos de prueba sin persona real",
                    "Sin localizar",
                    4.16,
                    -73.61,
                    false),
                true),
            new Entry(
                sample(
                    4,
                    "HELP_REQUEST",
                    "Solicitud de agua de demostración",
                    "Sector ficticio",
                    "Abierta",
                    4.13,
                    -73.64,
                    false),
                true),
            new Entry(
                sample(
                    5,
                    "ASSISTANCE",
                    "Refugio cerrado de demostración",
                    "Ubicación ficticia cercana",
                    "Cerrado",
                    4.1501,
                    -73.6301,
                    true),
                true),
            new Entry(
                sample(
                    6,
                    "ROAD",
                    "Reporte oculto de prueba",
                    "Ubicación ficticia",
                    "Bloqueada",
                    4.15,
                    -73.63,
                    false),
                false)));
  }

  PublicationService(List<Entry> publications) {
    this.publications = List.copyOf(publications);
  }

  private static Publication sample(
      long id,
      String category,
      String title,
      String location,
      String status,
      double latitude,
      double longitude,
      boolean closed) {
    return new Publication(
        id,
        category,
        title,
        location,
        status,
        "REPORTADA",
        "2026-10-05T23:00:00Z",
        latitude,
        longitude,
        "Información sintética para comprobar consulta, filtros y detalle. No describe una emergencia real.",
        "2026-10-05T22:00:00Z",
        closed);
  }

  public List<Publication> findPublications(
      String category,
      String operationalStatus,
      String confidenceLevel,
      boolean includeClosed,
      Bounds bounds) {
    String categoryFilter = normalize(category, CATEGORIES, "Categoría");
    String statusFilter = normalize(operationalStatus, STATUSES, "Estado");
    String confidenceFilter = normalize(confidenceLevel, CONFIDENCE, "Confianza");
    return publications.stream()
        .filter(Entry::visible)
        .map(Entry::publication)
        .filter(p -> includeClosed || !p.closed())
        .filter(p -> categoryFilter.isEmpty() || p.category().equals(categoryFilter))
        .filter(
            p ->
                statusFilter.isEmpty()
                    || p.operationalStatus().toUpperCase(Locale.ROOT).equals(statusFilter))
        .filter(p -> confidenceFilter.isEmpty() || p.confidenceLevel().equals(confidenceFilter))
        .filter(p -> bounds == null || bounds.contains(p.latitude(), p.longitude()))
        .toList();
  }

  public Publication findPublication(long id) {
    return publications.stream()
        .filter(Entry::visible)
        .map(Entry::publication)
        .filter(p -> p.id() == id)
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no disponible"));
  }

  private static String normalize(String value, Set<String> allowed, String label) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.isEmpty() && !allowed.contains(normalized))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " inválido");
    return normalized;
  }

  public record Bounds(double south, double west, double north, double east) {
    public Bounds {
      if (!Double.isFinite(south)
          || !Double.isFinite(west)
          || !Double.isFinite(north)
          || !Double.isFinite(east)
          || south < -90
          || north > 90
          || south > north
          || west < -180
          || west > 180
          || east < -180
          || east > 180) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Área inválida");
      }
    }

    boolean contains(double latitude, double longitude) {
      return latitude >= south
          && latitude <= north
          && (west <= east
              ? longitude >= west && longitude <= east
              : longitude >= west || longitude <= east);
    }
  }

  record Entry(Publication publication, boolean visible) {}

  public record Publication(
      long id,
      String category,
      String title,
      String location,
      String operationalStatus,
      String confidenceLevel,
      String updatedAt,
      double latitude,
      double longitude,
      String description,
      String createdAt,
      boolean closed) {}
}

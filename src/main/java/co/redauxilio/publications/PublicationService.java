package co.redauxilio.publications;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Consulta de datos sintéticos. Se sustituirá por un repositorio persistente. */
@Service
public class PublicationService {
  private static final Set<String> CATEGORIES =
      Set.of("ASSISTANCE", "ROAD", "MISSING_PERSON", "HELP_REQUEST");
  private final List<Publication> publications =
      List.of(
          new Publication(
              1,
              "ASSISTANCE",
              "Refugio de demostración",
              "Villavicencio · ubicación ficticia",
              "Disponible",
              "REPORTADA",
              "2026-10-05T23:00:00Z",
              4.15,
              -73.63),
          new Publication(
              2,
              "ROAD",
              "Vía afectada de demostración",
              "Tramo ficticio",
              "Bloqueada",
              "REPORTADA",
              "2026-10-05T23:00:00Z",
              4.14,
              -73.62),
          new Publication(
              3,
              "MISSING_PERSON",
              "Caso ficticio de persona desaparecida",
              "Datos de prueba sin persona real",
              "Sin localizar",
              "REPORTADA",
              "2026-10-05T23:00:00Z",
              4.16,
              -73.61),
          new Publication(
              4,
              "HELP_REQUEST",
              "Solicitud de agua de demostración",
              "Sector ficticio",
              "Abierta",
              "REPORTADA",
              "2026-10-05T23:00:00Z",
              4.13,
              -73.64));

  public List<Publication> findPublications(String category) {
    String normalized = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
    if (!normalized.isEmpty() && !CATEGORIES.contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría inválida");
    }
    return publications.stream()
        .filter(p -> normalized.isEmpty() || p.category().equals(normalized))
        .toList();
  }

  public record Publication(
      long id,
      String category,
      String title,
      String location,
      String status,
      String confidence,
      String updatedAt,
      double latitude,
      double longitude) {}
}

package co.redauxilio.publications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Valida publicaciones nuevas y aplica el cupo por cuenta antes de escribir una única fila. */
@Service
class PublicationCreationService {
  private static final int MAX_PER_HOUR = 5;
  private static final Duration WINDOW = Duration.ofHours(1);
  private static final Set<String> ASSISTANCE_SUBTYPES =
      Set.of("SHELTER", "COLLECTION", "MEDICAL_CENTER");
  private static final Set<String> ASSISTANCE_STATUSES =
      Set.of("DISPONIBLE", "CAPACIDAD LIMITADA", "LLENO");
  private static final Set<String> ROAD_STATUSES =
      Set.of("BLOQUEADA", "PARCIALMENTE HABILITADA", "HABILITADA");
  private static final Set<String> IMPACT_TYPES =
      Set.of("DERRUMBE", "INUNDACIÓN", "DAÑO ESTRUCTURAL", "OTRO");

  private final CitizenPublicationRepository repository;
  private final Clock clock;

  PublicationCreationService(CitizenPublicationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Recibe la cuenta autenticada y los campos del formulario. Rechaza valores inválidos sin
   * insertar nada y serializa el límite temporal mediante un bloqueo de la fila de cuenta.
   */
  @Transactional
  long create(String email, CreatePublicationRequest input) {
    CreatePublicationRequest request = validate(input);
    long authorId =
        repository
            .lockAuthor(email)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible"));
    Instant now = clock.instant();
    if (repository.countSince(authorId, now.minus(WINDOW)) >= MAX_PER_HOUR) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Máximo cinco publicaciones por cuenta y hora");
    }
    return repository.insert(authorId, request, now);
  }

  private static CreatePublicationRequest validate(CreatePublicationRequest input) {
    if (input == null) throw invalid("Faltan datos de la publicación");
    String category = required(input.category(), 20, "Categoría");
    String title = required(input.title(), 120, "Nombre o tramo");
    String description = required(input.description(), 1000, "Descripción");
    String location = required(input.location(), 160, "Ubicación");
    String status =
        required(input.operationalStatus(), 40, "Estado").toUpperCase(java.util.Locale.ROOT);
    Double latitude = input.latitude();
    Double longitude = input.longitude();
    if (latitude == null
        || longitude == null
        || !Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90
        || latitude > 90
        || longitude < -180
        || longitude > 180) throw invalid("Coordenadas inválidas");
    String subtype = input.assistanceSubtype();
    String impact = input.impactType();
    if ("ASSISTANCE".equals(category)) {
      subtype = required(subtype, 30, "Subtipo de asistencia");
      if (!ASSISTANCE_SUBTYPES.contains(subtype) || !ASSISTANCE_STATUSES.contains(status)) {
        throw invalid("Subtipo o disponibilidad inválidos");
      }
      if (impact != null && !impact.isBlank()) throw invalid("Un punto no admite afectación vial");
      impact = null;
    } else if ("ROAD".equals(category)) {
      impact = required(impact, 40, "Tipo de afectación").toUpperCase(java.util.Locale.ROOT);
      if (!IMPACT_TYPES.contains(impact) || !ROAD_STATUSES.contains(status)) {
        throw invalid("Afectación o transitabilidad inválidas");
      }
      if (subtype != null && !subtype.isBlank())
        throw invalid("Una vía no admite subtipo de punto");
      subtype = null;
    } else {
      throw invalid("Solo se pueden publicar puntos de asistencia o vías afectadas");
    }
    return new CreatePublicationRequest(
        category, subtype, title, description, location, status, impact, latitude, longitude);
  }

  private static String required(String value, int maxLength, String label) {
    if (value == null || value.isBlank()) throw invalid(label + " obligatorio");
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) throw invalid(label + " demasiado largo");
    return trimmed;
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

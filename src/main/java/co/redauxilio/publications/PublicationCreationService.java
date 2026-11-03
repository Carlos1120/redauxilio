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
    CreatePublicationRequest request = validate(input, clock);
    if ("HELP_REQUEST".equals(request.category()) && !"ABIERTA".equals(request.operationalStatus()))
      throw invalid("Una solicitud nueva inicia ABIERTA");
    if ("MISSING_PERSON".equals(request.category())
        && !"DESAPARECIDA".equals(request.operationalStatus()))
      throw invalid("Un caso nuevo inicia DESAPARECIDA");
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

  static CreatePublicationRequest validate(CreatePublicationRequest input) {
    return validate(input, Clock.systemUTC());
  }

  static CreatePublicationRequest validate(CreatePublicationRequest input, Clock clock) {
    if (input == null) throw invalid("Faltan datos de la publicación");
    String category = required(input.category(), 20, "Categoría");
    MissingPersonDetails missing =
        "MISSING_PERSON".equals(category)
            ? MissingPersonDetails.validate(input.missingPerson(), clock)
            : null;
    String title =
        required(missing == null ? input.title() : missing.referenceName(), 120, "Nombre o tramo");
    String description =
        required(
            missing == null ? input.description() : missing.physicalDescription(),
            1000,
            "Descripción");
    String location =
        required(missing == null ? input.location() : missing.lastSeenPlace(), 160, "Ubicación");
    String status =
        required(
                ("HELP_REQUEST".equals(category) || "MISSING_PERSON".equals(category))
                        && (input.operationalStatus() == null
                            || input.operationalStatus().isBlank())
                    ? ("MISSING_PERSON".equals(category) ? "DESAPARECIDA" : "ABIERTA")
                    : input.operationalStatus(),
                40,
                "Estado")
            .toUpperCase(java.util.Locale.ROOT);
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
    // RA-09: no persistir precisión GPS domiciliaria en el caso público (~110 m en latitud).
    if (missing != null) {
      latitude = Math.round(latitude * 1000d) / 1000d;
      longitude = Math.round(longitude * 1000d) / 1000d;
    }
    String subtype = input.assistanceSubtype();
    String impact = input.impactType();
    String need = null, quantity = null, urgency = null, reference = null;
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
    } else if ("HELP_REQUEST".equals(category)) {
      need = required(input.needType(), 20, "Tipo de necesidad").toUpperCase(java.util.Locale.ROOT);
      urgency =
          required(input.declaredUrgency(), 10, "Urgencia declarada")
              .toUpperCase(java.util.Locale.ROOT);
      reference = required(input.requesterReference(), 120, "Referencia pública");
      quantity =
          input.approximateQuantity() == null || input.approximateQuantity().isBlank()
              ? null
              : required(input.approximateQuantity(), 120, "Cantidad aproximada");
      if (!Set.of("AGUA", "ALIMENTOS", "REFUGIO", "SALUD", "TRANSPORTE", "OTRO").contains(need)
          || !Set.of("BAJA", "MEDIA", "ALTA").contains(urgency)
          || !Set.of("ABIERTA", "EN ATENCIÓN", "SATISFECHA").contains(status))
        throw invalid("Necesidad, urgencia o estado inválidos");
      if (reference.contains("@") || reference.matches(".*(?:[0-9][\\s().+-]*){7,}.*"))
        throw invalid("Usa un alias o comunidad sin correo, teléfono ni documento");
      subtype = null;
      impact = null;
    } else if ("MISSING_PERSON".equals(category)) {
      if (!Set.of("DESAPARECIDA", "ENCONTRADA", "CASO CERRADO").contains(status))
        throw invalid("Estado de persona desaparecida inválido");
      subtype = null;
      impact = null;
    } else {
      throw invalid("Categoría no disponible para publicación");
    }
    return new CreatePublicationRequest(
        category,
        subtype,
        title,
        description,
        location,
        status,
        impact,
        latitude,
        longitude,
        need,
        quantity,
        urgency,
        reference,
        missing);
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

package co.redauxilio.publications;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Datos públicos mínimos del caso; el contacto nunca pertenece a esta proyección ni al borrador.
 */
public record MissingPersonDetails(
    String referenceName,
    String ageRange,
    String physicalDescription,
    String lastSeenPlace,
    String lastSeenAt) {
  private static final Set<String> AGE_RANGES =
      Set.of("0–5", "6–12", "13–17", "18–29", "30–59", "60+");
  private static final Pattern SENSITIVE =
      Pattern.compile(
          "@|(?:[0-9][\\s().+-]*){7,}|c[eé]dula|documento de identidad|historia cl[ií]nica|diagn[oó]stico|domicilio|direcci[oó]n residencial|vive en",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Normaliza límites/catálogos y exige fecha no futura con el mismo Clock del dominio. */
  static MissingPersonDetails validate(MissingPersonDetails input, Clock clock) {
    if (input == null) throw invalid("Faltan los datos de la persona desaparecida");
    String name = validatePublicText(input.referenceName(), 120, "Nombre o referencia");
    String description = validatePublicText(input.physicalDescription(), 500, "Descripción física");
    String place = validatePublicText(input.lastSeenPlace(), 160, "Último sector conocido");
    String age =
        input.ageRange() == null || input.ageRange().isBlank() ? null : input.ageRange().trim();
    if (age != null && !AGE_RANGES.contains(age)) throw invalid("Rango de edad no permitido");
    try {
      Instant seen = Instant.parse(input.lastSeenAt() == null ? "" : input.lastSeenAt());
      if (seen.isAfter(clock.instant())) throw invalid("La última vez vista no puede ser futura");
      return new MissingPersonDetails(name, age, description, place, seen.toString());
    } catch (DateTimeParseException failure) {
      throw invalid("Indica fecha y hora aproximadas válidas");
    }
  }

  /** Los motivos también llegan al historial: nunca admiten contactos/identificadores evidentes. */
  static void validateReason(String reason) {
    if (reason != null && SENSITIVE.matcher(reason).find())
      throw invalid("Evita datos sensibles en el motivo del caso");
  }

  /** Reutiliza los límites y la detección de datos sensibles de textos visibles del caso. */
  static String validatePublicText(String text, int max, String label) {
    if (text == null || text.isBlank() || text.trim().length() > max)
      throw invalid(label + " obligatorio o demasiado largo");
    if (SENSITIVE.matcher(text).find())
      throw invalid(
          "Evita identificadores, contactos, datos médicos y domicilios privados en el caso público");
    return text.trim();
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

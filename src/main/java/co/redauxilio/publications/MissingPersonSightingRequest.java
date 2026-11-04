package co.redauxilio.publications;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Datos de entrada de un avistamiento, separados del estado operativo del caso. */
public record MissingPersonSightingRequest(
    String observedAt, String location, Double latitude, Double longitude, String description) {

  /** Valida la fecha, el punto aproximado y el texto antes de guardar el evento. */
  MissingPersonSightingRequest validate(Clock clock) {
    Instant observed;
    try {
      observed = Instant.parse(observedAt == null ? "" : observedAt);
    } catch (DateTimeParseException failure) {
      throw invalid("Indica una fecha y hora válidas para el avistamiento.");
    }
    if (observed.isAfter(clock.instant()))
      throw invalid("La fecha del avistamiento no puede ser futura.");
    if (latitude == null
        || longitude == null
        || !Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90
        || latitude > 90
        || longitude < -180
        || longitude > 180) throw invalid("Las coordenadas del avistamiento no son válidas.");

    return new MissingPersonSightingRequest(
        observed.toString(),
        MissingPersonDetails.validatePublicText(location, 160, "Sector o lugar público"),
        Math.round(latitude * 1000d) / 1000d,
        Math.round(longitude * 1000d) / 1000d,
        MissingPersonDetails.validatePublicText(description, 1000, "Descripción del avistamiento"));
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

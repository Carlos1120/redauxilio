package co.redauxilio.publications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Vigencia derivada sin escrituras: admite futuras fuentes de actividad válida sin cambiar
 * confianza.
 */
record PublicationFreshness(String status, String activityAt, String expiresAt) {
  static PublicationFreshness evaluate(
      String category, Instant updatedAt, Instant latestValidEvidenceAt, Clock clock) {
    Instant activity =
        latestValidEvidenceAt != null && latestValidEvidenceAt.isAfter(updatedAt)
            ? latestValidEvidenceAt
            : updatedAt;
    Instant expires = activity.plus(Duration.ofHours("MISSING_PERSON".equals(category) ? 72 : 24));
    return new PublicationFreshness(
        clock.instant().isAfter(expires) ? "DESACTUALIZADA" : "VIGENTE",
        activity.toString(),
        expires.toString());
  }
}

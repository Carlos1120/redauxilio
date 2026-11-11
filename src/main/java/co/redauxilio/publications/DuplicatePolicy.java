package co.redauxilio.publications;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

/**
 * Comparaciones exactas RN-26: normalizacion textual y limites inclusivos, sin similitud difusa.
 */
final class DuplicatePolicy {
  private DuplicatePolicy() {}

  static String normalize(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .strip()
        .replaceAll("\\s+", " ");
  }

  static boolean withinWindow(Instant created, Instant now) {
    return !created.isAfter(now) && !created.isBefore(now.minusSeconds(86400));
  }

  static double distance(double lat1, double lon1, double lat2, double lon2) {
    double a =
        Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
    return 6371000d * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
  }

  static boolean matchesPerson(MissingPersonDetails a, MissingPersonDetails b) {
    if (!normalize(a.referenceName()).isEmpty()
        && normalize(a.referenceName()).equals(normalize(b.referenceName()))) return true;
    return a.ageRange() != null
        && !a.ageRange().isBlank()
        && a.ageRange().equals(b.ageRange())
        && !normalize(a.physicalDescription()).isEmpty()
        && normalize(a.physicalDescription()).equals(normalize(b.physicalDescription()))
        && !normalize(a.lastSeenPlace()).isEmpty()
        && normalize(a.lastSeenPlace()).equals(normalize(b.lastSeenPlace()));
  }
}

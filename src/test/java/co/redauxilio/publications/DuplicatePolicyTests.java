package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** RED inicial: politica inexistente; usa reflexion para fallar por comportamiento ausente. */
class DuplicatePolicyTests {
  @Test
  void normalizesSpanishNames() throws Exception {
    var type = Class.forName("co.redauxilio.publications.DuplicatePolicy");
    var method = type.getDeclaredMethod("normalize", String.class);
    method.setAccessible(true);
    assertEquals("jose maria", method.invoke(null, "  JOSÉ,  María! "));
  }

  @Test
  void geographicBoundaries() throws Exception {
    var type = Class.forName("co.redauxilio.publications.DuplicatePolicy");
    var method = type.getDeclaredMethod("withinWindow", Instant.class, Instant.class);
    method.setAccessible(true);
    var now = Instant.parse("2026-11-11T12:00:00Z");
    assertEquals(true, method.invoke(null, now.minusSeconds(86400), now));
    assertEquals(false, method.invoke(null, now.minusSeconds(86401), now));
    assertEquals(false, method.invoke(null, now.plusSeconds(1), now));
  }

  @Test
  void exactDistanceBoundaryAndCategoriesAreIndependentOfText() {
    double degrees = Math.toDegrees(250d / 6371000d);
    assertEquals(250d, DuplicatePolicy.distance(0, 0, degrees, 0), 0.000001);
    assertTrue(DuplicatePolicy.distance(0, 0, Math.toDegrees(251d / 6371000d), 0) > 250);
  }

  @Test
  void missingPersonConditionsDoNotRequireEqualDates() {
    var first =
        new MissingPersonDetails(
            "José, Maria", "18–29", "Chaqueta gris", "Parque Central", "2026-01-01T00:00:00Z");
    assertTrue(
        DuplicatePolicy.matchesPerson(
            first,
            new MissingPersonDetails(
                " JOSE maria! ", null, "Otra ropa", "Otro sector", "2020-01-01T00:00:00Z")));
    assertTrue(
        DuplicatePolicy.matchesPerson(
            first,
            new MissingPersonDetails(
                "Otra referencia",
                "18–29",
                "CHAQUETA GRÍS!",
                "parque central",
                "2020-01-01T00:00:00Z")));
    assertFalse(
        DuplicatePolicy.matchesPerson(
            first,
            new MissingPersonDetails(
                "Otra referencia",
                null,
                "Chaqueta gris",
                "Parque Central",
                "2020-01-01T00:00:00Z")));
    assertFalse(
        DuplicatePolicy.matchesPerson(
            first,
            new MissingPersonDetails(
                "Otra referencia", "18–29", "", "Parque Central", "2020-01-01T00:00:00Z")));
    assertFalse(
        DuplicatePolicy.matchesPerson(
            first,
            new MissingPersonDetails(
                "Otra referencia",
                "18–29",
                "Chaqueta gris",
                "Otro sector",
                "2020-01-01T00:00:00Z")));
  }
}

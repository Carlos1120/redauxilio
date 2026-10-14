package co.redauxilio.geography;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Prueba caché y fallo de búsqueda con un proveedor local; nunca consulta Nominatim público. */
class PlaceSearchTests {
  @Test
  void repeatQueryUsesCacheAndProviderFailureDoesNotProduceAPlace() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    List<Long> requestTimes = Collections.synchronizedList(new ArrayList<>());
    HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/search",
        exchange -> {
          calls.incrementAndGet();
          requestTimes.add(System.nanoTime());
          boolean failure = exchange.getRequestURI().getRawQuery().contains("fallo");
          byte[] body =
              (failure
                      ? "error"
                      : "[{\"display_name\":\"Parque central\",\"lat\":\"4.15\",\"lon\":\"-73.63\"}]")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(failure ? 503 : 200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    provider.start();
    try {
      PlaceSearchService service =
          new PlaceSearchService(
              "http://127.0.0.1:" + provider.getAddress().getPort() + "/search",
              true,
              Clock.systemUTC(),
              new ObjectMapper());
      assertEquals("Parque central", service.search("Parque central").get(0).label());
      assertEquals(1, service.search("  parque  central ").size());
      assertEquals(1, calls.get());
      ResponseStatusException error =
          assertThrows(ResponseStatusException.class, () -> service.search("fallo"));
      assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
      assertEquals(2, calls.get());
      assertTrue(requestTimes.get(1) - requestTimes.get(0) >= Duration.ofMillis(900).toNanos());
    } finally {
      provider.stop(0);
    }
  }

  @Test
  void disabledProviderRejectsWithoutNetwork() {
    PlaceSearchService service =
        new PlaceSearchService(
            "http://127.0.0.1:1/search", false, Clock.systemUTC(), new ObjectMapper());
    assertEquals(
        HttpStatus.SERVICE_UNAVAILABLE,
        assertThrows(ResponseStatusException.class, () -> service.search("Villavicencio"))
            .getStatusCode());
  }
}

package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.redauxilio.RedAuxilioApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Carga HTTP real y local: 20 usuarios de circuito cerrado, 3 s de calentamiento y 10 s medidos.
 * Mezcla consulta, filtros y detalle; valida respuesta y distingue tiempo interno de HTTP cliente.
 * No solicita mapas/CDN. El informe JSON queda en target y puede reproducirse con Maven verify.
 */
@SpringBootTest(
    classes = {RedAuxilioApplication.class, Ra02ValidationApplication.Fixture.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ra02PerformanceTests {
  @LocalServerPort private int port;
  private final ObjectMapper json = new ObjectMapper();

  record Sample(String operation, double serverMillis, double clientMillis) {}

  @Test
  void measuresThousandPublicationsWithTwentyConcurrentUsers() throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    String base = "http://localhost:" + port + "/api/publications";
    var initial =
        client.send(
            HttpRequest.newBuilder(URI.create(base)).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(900, json.readTree(initial.body()).size());
    var allVisible =
        client.send(
            HttpRequest.newBuilder(URI.create(base + "?includeClosed=true")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(950, json.readTree(allVisible.body()).size());
    List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
    var workers = Executors.newFixedThreadPool(20);
    long warmEnd = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    long end = warmEnd + Duration.ofSeconds(10).toNanos();
    List<Future<?>> jobs = new ArrayList<>();
    try {
      for (int user = 0; user < 20; user++) {
        final int offset = user;
        jobs.add(
            workers.submit(
                () -> {
                  int sequence = offset;
                  while (System.nanoTime() < end) {
                    int operation = sequence++ % 3;
                    String suffix =
                        switch (operation) {
                          case 0 -> "?south=4.12&west=-73.66&north=4.18&east=-73.59";
                          case 1 ->
                              "?category=ROAD&operationalStatus=Bloqueada&confidenceLevel=REPORTADA";
                          default -> "/1";
                        };
                    long start = System.nanoTime();
                    try {
                      var response =
                          client.send(
                              HttpRequest.newBuilder(URI.create(base + suffix))
                                  .timeout(Duration.ofSeconds(5))
                                  .build(),
                              HttpResponse.BodyHandlers.ofString());
                      double clientMillis = (System.nanoTime() - start) / 1_000_000.0;
                      assertEquals(200, response.statusCode());
                      var body = json.readTree(response.body());
                      if (operation == 2) assertEquals(1, body.get("id").asInt());
                      else if (operation == 0) assertEquals(900, body.size());
                      else {
                        assertEquals(200, body.size());
                        for (var item : body) {
                          assertEquals("ROAD", item.get("category").asText());
                          assertEquals("Bloqueada", item.get("operationalStatus").asText());
                          assertEquals("REPORTADA", item.get("confidenceLevel").asText());
                        }
                      }
                      double serverMillis =
                          Double.parseDouble(
                              response
                                  .headers()
                                  .firstValue("Server-Timing")
                                  .orElseThrow()
                                  .split("dur=")[1]);
                      if (start >= warmEnd)
                        samples.add(
                            new Sample(
                                new String[] {"consulta", "filtros", "detalle"}[operation],
                                serverMillis,
                                clientMillis));
                    } catch (Exception error) {
                      throw new IllegalStateException(error);
                    }
                  }
                }));
      }
      for (var job : jobs) job.get();
    } finally {
      workers.shutdownNow();
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("fixture", 1000);
    report.put("virtualUsers", 20);
    report.put("warmupSeconds", 3);
    report.put("measurementSeconds", 10);
    report.put("errors", 0);
    report.put("samples", samples.size());
    for (String operation : List.of("consulta", "filtros", "detalle")) {
      var selected = samples.stream().filter(s -> s.operation().equals(operation)).toList();
      assertTrue(selected.size() >= 20, "Muestras insuficientes para " + operation);
      double serverP95 = percentile(selected.stream().map(Sample::serverMillis).toList(), .95);
      report.put(
          operation,
          Map.of(
              "samples",
              selected.size(),
              "serverP95Millis",
              serverP95,
              "clientP95Millis",
              percentile(selected.stream().map(Sample::clientMillis).toList(), .95),
              "serverP50Millis",
              percentile(selected.stream().map(Sample::serverMillis).toList(), .50),
              "serverP99Millis",
              percentile(selected.stream().map(Sample::serverMillis).toList(), .99)));
      assertTrue(serverP95 <= 2000, operation + " supera p95 servidor 2 s");
    }
    Files.createDirectories(Path.of("target"));
    json.writerWithDefaultPrettyPrinter()
        .writeValue(Path.of("target/ra02-performance.json").toFile(), report);
  }

  /** Percentil por rango más próximo, sobre milisegundos ordenados; no es un promedio. */
  private static double percentile(List<Double> values, double quantile) {
    var sorted = values.stream().sorted().toList();
    return sorted.get((int) Math.ceil(sorted.size() * quantile) - 1);
  }
}

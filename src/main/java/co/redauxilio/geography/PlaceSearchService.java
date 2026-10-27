package co.redauxilio.geography;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Busca lugares solo ante una acción explícita. Un único bloqueo por instancia comparte la cuota de
 * Nominatim entre usuarios; resultados repetidos se sirven de una caché temporal y acotada.
 */
@Service
public class PlaceSearchService {
  private static final Duration CACHE_LIFETIME = Duration.ofHours(24);
  private static final long MIN_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();
  private static final String USER_AGENT =
      "RedAuxilio/0.1 (+https://github.com/Carlos1120/redauxilio)";

  private final URI endpoint;
  private final boolean enabled;
  private final Clock clock;
  private final HttpClient client;
  private final ObjectMapper json;
  private final Map<String, CachedPlaces> cache =
      new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedPlaces> eldest) {
          return size() > 200;
        }
      };
  private long nextNetworkRequestNanos;

  public PlaceSearchService(
      @Value("${redauxilio.geocoding.search-url:https://nominatim.openstreetmap.org/search}")
          String searchUrl,
      @Value("${redauxilio.geocoding.enabled:true}") boolean enabled,
      Clock clock,
      ObjectMapper json) {
    this.endpoint = URI.create(searchUrl);
    this.enabled = enabled;
    this.clock = clock;
    this.json = json;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  /** Devuelve hasta cinco lugares; nunca bloquea la elección manual si el proveedor falla. */
  public synchronized List<Place> search(String query) {
    if (!enabled) throw unavailable();
    if (query == null || query.isBlank() || query.trim().length() < 3 || query.length() > 160) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe entre 3 y 160 caracteres");
    }
    String normalized = query.trim().replaceAll("\\s+", " ");
    String key = normalized.toLowerCase(Locale.ROOT);
    CachedPlaces saved = cache.get(key);
    if (saved != null && clock.instant().isBefore(saved.expiresAt())) return saved.places();
    waitForTurn();
    try {
      String url =
          endpoint
              + "?format=jsonv2&limit=5&q="
              + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(6))
              .header("User-Agent", USER_AGENT)
              .header("Accept", "application/json")
              .header("Accept-Language", "es")
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) throw unavailable();
      List<Place> places = readPlaces(response.body());
      cache.put(key, new CachedPlaces(places, clock.instant().plus(CACHE_LIFETIME)));
      return places;
    } catch (IOException exception) {
      throw unavailable();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } finally {
      // Esperar desde el fin evita ráfagas si la conexión inicial tardó en llegar al proveedor.
      nextNetworkRequestNanos = System.nanoTime() + MIN_INTERVAL_NANOS;
    }
  }

  private void waitForTurn() {
    long remaining = nextNetworkRequestNanos - System.nanoTime();
    if (remaining > 0) {
      try {
        TimeUnit.NANOSECONDS.sleep(remaining);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw unavailable();
      }
    }
  }

  private List<Place> readPlaces(String body) throws IOException {
    JsonNode root = json.readTree(body);
    if (!root.isArray()) throw new IOException("Formato de geocodificación inválido");
    List<Place> places = new ArrayList<>();
    for (JsonNode item : root) {
      String label = item.path("display_name").asText("");
      double latitude = item.path("lat").asDouble(Double.NaN);
      double longitude = item.path("lon").asDouble(Double.NaN);
      if (!label.isBlank()
          && Double.isFinite(latitude)
          && Double.isFinite(longitude)
          && latitude >= -90
          && latitude <= 90
          && longitude >= -180
          && longitude <= 180) {
        places.add(new Place(label, latitude, longitude));
      }
    }
    return List.copyOf(places);
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Búsqueda no disponible; marca el punto manualmente");
  }

  private record CachedPlaces(List<Place> places, Instant expiresAt) {}

  /** Resultado público sin identificadores del proveedor; la ubicación sigue siendo editable. */
  public record Place(String label, double latitude, double longitude) {}
}

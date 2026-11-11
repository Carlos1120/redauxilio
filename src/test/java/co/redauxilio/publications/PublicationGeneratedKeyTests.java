package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regresión del ID generado mediante HTTP real y SQL. Por defecto usa H2; las variables
 * RA_KEY_TEST_DB_* permiten repetir únicamente esta clase en una base PostgreSQL exclusiva. No
 * elimina datos existentes y cada prueba crea una cuenta ficticia independiente.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.sql.init.mode=always")
class PublicationGeneratedKeyTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "RA_KEY_TEST_DB_URL",
                    "jdbc:h2:mem:generatedkey;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
    registry.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("RA_KEY_TEST_DB_USERNAME", "sa"));
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("RA_KEY_TEST_DB_PASSWORD", ""));
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder passwords;
  @Autowired TransactionTemplate transaction;
  @Autowired PublicationCreationService creation;
  HttpClient client;
  String email;
  long authorId;

  @BeforeEach
  void accountAndSession() throws Exception {
    email = "key-" + UUID.randomUUID() + "@example.invalid";
    String password = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO citizen_account(email,display_name,password_hash,created_at) VALUES (?,?,?,?)",
        email,
        "Cuenta ficticia de regresión",
        passwords.encode(password),
        Timestamp.from(Instant.now()));
    authorId =
        jdbc.queryForObject("SELECT id FROM citizen_account WHERE email=?", Long.class, email);
    client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    JsonNode csrf = csrf();
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username="
                            + URLEncoder.encode(email, StandardCharsets.UTF_8)
                            + "&password="
                            + URLEncoder.encode(password, StandardCharsets.UTF_8)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(302, response.statusCode());
    assertFalse(response.headers().firstValue("Location").orElse("").contains("error"));
  }

  /** El ID devuelto debe identificar exactamente la fila del autor y su proyección pública. */
  @ParameterizedTest
  @ValueSource(strings = {"ASSISTANCE", "ROAD", "HELP_REQUEST", "MISSING_PERSON"})
  void createsEachCategoryWithPersistedId(String category) throws Exception {
    HttpResponse<String> response = post(payload(category));
    assertEquals(201, response.statusCode(), response.body());
    JsonNode created = json.readTree(response.body());
    long id = created.path("id").asLong();
    assertTrue(id >= 1_000_000);
    assertEquals(
        authorId,
        jdbc.queryForObject(
            "SELECT author_id FROM citizen_publication WHERE id=?", Long.class, id));
    assertEquals(
        category,
        jdbc.queryForObject(
            "SELECT category FROM citizen_publication WHERE id=?", String.class, id));
    HttpResponse<String> publicRead = get("/api/publications/" + id);
    assertEquals(200, publicRead.statusCode());
    assertEquals(id, json.readTree(publicRead.body()).path("id").asLong());
    assertEquals(category, json.readTree(publicRead.body()).path("category").asText());
    assertFalse(json.readTree(publicRead.body()).has("authorId"));
    HttpResponse<String> ownRead = get("/api/my/publications");
    assertEquals(200, ownRead.statusCode());
    assertEquals(id, json.readTree(ownRead.body()).get(0).path("id").asLong());
    assertEquals(1, countOwn());
  }

  /** Cuatro validaciones negativas no reservan publicaciones ni dejan cambios parciales. */
  @Test
  void invalidInputsLeaveNoPartialPublication() throws Exception {
    for (String category : List.of("ASSISTANCE", "ROAD", "HELP_REQUEST", "MISSING_PERSON")) {
      Map<String, Object> invalid = payload(category);
      invalid.put("latitude", 91);
      assertEquals(400, post(invalid).statusCode());
      assertEquals(0, countOwn());
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE actor_id=?",
            Integer.class,
            authorId));
    assertEquals(201, post(payload("MISSING_PERSON")).statusCode());
    assertEquals(1, countOwn());
  }

  /** Una excepción posterior al INSERT revierte también la fila cuyo ID ya fue obtenido. */
  @Test
  void generatedIdDoesNotCommitOutsideTransaction() throws Exception {
    CreatePublicationRequest request =
        json.convertValue(payload("MISSING_PERSON"), CreatePublicationRequest.class);
    assertThrows(
        IllegalStateException.class,
        () ->
            transaction.execute(
                status -> {
                  long id = creation.create(email, request);
                  assertTrue(id >= 1_000_000);
                  throw new IllegalStateException("Fallo controlado después de insertar");
                }));
    assertEquals(0, countOwn());
    assertEquals(201, post(payload("MISSING_PERSON")).statusCode());
    assertEquals(1, countOwn());
  }

  private int countOwn() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM citizen_publication WHERE author_id=?", Integer.class, authorId);
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode csrf() throws Exception {
    return json.readTree(get("/csrf").body());
  }

  private HttpResponse<String> post(Map<String, Object> body) throws Exception {
    JsonNode csrf = csrf();
    return client.send(
        HttpRequest.newBuilder(uri("/api/publications"))
            .header("Content-Type", "application/json")
            .header(csrf.path("headerName").asText(), csrf.path("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private Map<String, Object> payload(String category) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("category", category);
    body.put("title", "Referencia ficticia");
    body.put("description", "Descripción ficticia de prueba");
    body.put("location", "Parque público ficticio");
    body.put("latitude", 4.15);
    body.put("longitude", -73.63);
    switch (category) {
      case "ASSISTANCE" -> {
        body.put("assistanceSubtype", "SHELTER");
        body.put("operationalStatus", "DISPONIBLE");
      }
      case "ROAD" -> {
        body.put("impactType", "DERRUMBE");
        body.put("operationalStatus", "BLOQUEADA");
      }
      case "HELP_REQUEST" -> {
        body.put("needType", "AGUA");
        body.put("declaredUrgency", "ALTA");
        body.put("requesterReference", "Comunidad ficticia");
      }
      case "MISSING_PERSON" ->
          body.put(
              "missingPerson",
              Map.of(
                  "referenceName",
                      "Referencia ficticia "
                          + UUID.randomUUID().toString().replaceAll("[0-9]", "x"),
                  "physicalDescription", "Chaqueta gris de prueba",
                  "lastSeenPlace", "Parque público ficticio",
                  "lastSeenAt", "2026-11-02T12:30:00Z"));
      default -> throw new IllegalArgumentException(category);
    }
    return body;
  }
}

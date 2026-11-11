package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.redauxilio.identity.OperatorPermissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** RA-09: HTTP/SQL reales, Clock controlado y datos ficticios en H2 exclusiva. */
@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:ra09;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(PublicationFreshnessTests.TimeConfig.class)
class MissingPersonTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PublicationFreshnessTests.TestClock clock;
  @Autowired OperatorPermissions operators;
  String author;
  static final Instant START = Instant.parse("2026-11-03T12:00:00Z");

  @BeforeEach
  void fixtures() {
    for (String table :
        List.of(
            "publication_authorized_contact",
            "publication_moderation_action",
            "publication_confirmation_invalidation",
            "publication_confirmation",
            "publication_content_report",
            "citizen_publication_change",
            "citizen_publication",
            "operator_permission_audit",
            "operator_permission",
            "citizen_account")) jdbc.update("DELETE FROM " + table);
    clock.now = START;
    author = account();
  }

  String account() {
    String email = "ra09-" + UUID.randomUUID() + "@example.invalid";
    jdbc.update(
        "INSERT INTO citizen_account(email,display_name,password_hash,created_at) VALUES (?,'Ficticio','no-login',?)",
        email,
        Timestamp.from(START));
    return email;
  }

  long accountId(String email) {
    return jdbc.queryForObject("SELECT id FROM citizen_account WHERE email=?", Long.class, email);
  }

  Map<String, Object> person() {
    var p = new LinkedHashMap<String, Object>();
    p.put("referenceName", "Persona de prueba");
    p.put("ageRange", "18–29");
    p.put("physicalDescription", "Chaqueta gris y gorra blanca");
    p.put("lastSeenPlace", "Parque público ficticio");
    p.put("lastSeenAt", START.minusSeconds(3600).toString());
    return p;
  }

  Map<String, Object> body() {
    var b = new LinkedHashMap<String, Object>();
    b.put("category", "MISSING_PERSON");
    b.put("latitude", 4.15);
    b.put("longitude", -73.63);
    b.put("missingPerson", person());
    return b;
  }

  long create() throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/publications")
                        .with(user(author))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationalStatus").value("DESAPARECIDA"))
                .andExpect(jsonPath("$.missingPerson.referenceName").value("Persona de prueba"))
                .andExpect(jsonPath("$.authorId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asLong();
  }

  void transition(long id, String target, String reason, int expected) throws Exception {
    mvc.perform(
            post("/api/my/publications/{id}/status", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(Map.of("operationalStatus", target, "reason", reason))))
        .andExpect(status().is(expected));
  }

  void contact(long id, String email, String value, boolean consent, boolean adult, int expected)
      throws Exception {
    mvc.perform(
            put("/api/my/publications/{id}/authorized-contact", id)
                .with(user(email))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("value", value, "consent", consent, "adultContact", adult))))
        .andExpect(status().is(expected));
  }

  @Test
  void validationAndQuota() throws Exception {
    for (String field :
        List.of("referenceName", "physicalDescription", "lastSeenPlace", "lastSeenAt")) {
      var b = body();
      var p = person();
      p.put(field, " ");
      b.put("missingPerson", p);
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isBadRequest());
    }
    for (var entry :
        Map.of(
                "referenceName",
                "x".repeat(121),
                "physicalDescription",
                "x".repeat(501),
                "lastSeenPlace",
                "x".repeat(161),
                "ageRange",
                "18 años exactos",
                "lastSeenAt",
                START.plusNanos(1).toString())
            .entrySet()) {
      var b = body();
      var p = person();
      p.put(entry.getKey(), entry.getValue());
      b.put("missingPerson", p);
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isBadRequest());
    }
    for (String sensitive :
        List.of(
            "correo@example.invalid",
            "Contacto 3001234567",
            "Cédula privada",
            "Historia clínica ficticia",
            "domicilio residencial")) {
      var b = body();
      var p = person();
      p.put("physicalDescription", sensitive);
      b.put("missingPerson", p);
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(b)))
          .andExpect(status().isBadRequest());
    }
    var b = body();
    b.put("operationalStatus", "ENCONTRADA");
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(b)))
        .andExpect(status().isBadRequest());
    b = body();
    b.put("latitude", 91);
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(b)))
        .andExpect(status().isBadRequest());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class));
    for (int i = 0; i < 5; i++) {
      var separated = body();
      var person = person();
      person.put("referenceName", "Persona cuota " + i);
      person.put("lastSeenPlace", "Sector cuota " + i);
      separated.put("missingPerson", person);
      mvc.perform(
              post("/api/publications")
                  .with(user(author))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(separated)))
          .andExpect(status().isCreated());
    }
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body())))
        .andExpect(status().isTooManyRequests());
  }

  @ParameterizedTest
  @CsvSource({
    "DESAPARECIDA,ENCONTRADA,true",
    "DESAPARECIDA,CASO CERRADO,true",
    "ENCONTRADA,DESAPARECIDA,true",
    "ENCONTRADA,CASO CERRADO,true",
    "CASO CERRADO,DESAPARECIDA,true",
    "CASO CERRADO,ENCONTRADA,false",
    "DESAPARECIDA,DESAPARECIDA,false",
    "ENCONTRADA,ENCONTRADA,false",
    "DESAPARECIDA,ABIERTA,false"
  })
  void transitions(String from, String to, boolean accepted) throws Exception {
    long id = create();
    jdbc.update(
        "UPDATE citizen_publication SET operational_status=?,closed=? WHERE id=?",
        from,
        from.equals("CASO CERRADO"),
        id);
    if (to.equals("DESAPARECIDA") && accepted) transition(id, to, "", 400);
    transition(id, to, "Motivo ficticio", accepted ? 200 : 400);
    assertEquals(
        accepted ? to : from,
        jdbc.queryForObject(
            "SELECT operational_status FROM citizen_publication WHERE id=?", String.class, id));
    assertEquals(
        (accepted ? to : from).equals("CASO CERRADO"),
        jdbc.queryForObject(
            "SELECT closed FROM citizen_publication WHERE id=?", Boolean.class, id));
    assertEquals(
        accepted ? 1 : 0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id=?",
            Integer.class,
            id));
  }

  @Test
  void noOpInvalidationAnd72Hours() throws Exception {
    long id = create();
    for (int i = 0; i < 3; i++)
      mvc.perform(
              post("/api/publications/{id}/confirmations", id)
                  .with(user(account()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
          .andExpect(status().isOk());
    var edit = body();
    edit.put("operationalStatus", "DESAPARECIDA");
    clock.now = START.plus(Duration.ofHours(72));
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"));
    clock.now = clock.now.plusNanos(1);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("DESACTUALIZADA"))
        .andExpect(jsonPath("$.confirmationCount").value(3))
        .andExpect(jsonPath("$.confirmationVersion").value(1));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication_change", Integer.class));
    var p = person();
    p.put("ageRange", null);
    p.put("physicalDescription", "Chaqueta azul y gorra blanca");
    edit.put("missingPerson", p);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freshnessStatus").value("VIGENTE"))
        .andExpect(jsonPath("$.confirmationVersion").value(2))
        .andExpect(jsonPath("$.confirmationCount").value(0));
    assertEquals(
        3, jdbc.queryForObject("SELECT COUNT(*) FROM publication_confirmation", Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_confirmation_invalidation", Integer.class));
    assertTrue(
        jdbc.queryForObject("SELECT after_snapshot FROM citizen_publication_change", String.class)
            .contains("Chaqueta azul"));
  }

  @Test
  void consentPrivacyWithdrawalAndRollback() throws Exception {
    long id = create();
    String secret = "adulto.ficticio@example.invalid";
    contact(id, author, secret, false, true, 400);
    contact(id, author, secret, true, false, 400);
    contact(id, author, "no permitido", true, true, 400);
    contact(id, account(), secret, true, true, 404);
    mvc.perform(
            put("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/my/publications/{id}/authorized-contact", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consent\":{}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"));
    contact(id, author, secret, true, true, 204);
    contact(id, author, secret, true, true, 204);
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.value").value(secret));
    for (String url :
        List.of(
            "/api/publications",
            "/api/publications/" + id,
            "/api/my/publications",
            "/api/my/publications/" + id + "/history")) {
      String out =
          mvc.perform(get(url).with(user(author)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(out.contains(secret));
      assertFalse(out.contains("contact_value"));
    }
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication_change", Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT confirmation_version FROM citizen_publication WHERE id=?", Integer.class, id));
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT ra09_contact_rollback CHECK(action <> 'CONTACT_WITHDRAW')");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () ->
              mvc.perform(
                  delete("/api/my/publications/{id}/authorized-contact", id)
                      .with(user(author))
                      .with(csrf())));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT ra09_contact_rollback");
    }
    assertEquals(
        secret,
        jdbc.queryForObject(
            "SELECT contact_value FROM publication_authorized_contact WHERE publication_id=?",
            String.class,
            id));
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound());
    String history =
        mvc.perform(get("/api/my/publications/{id}/history", id).with(user(author)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(history.contains(secret));
    assertTrue(history.contains("CONTACT_WITHDRAW"));
    assertTrue(history.contains("contactAuthorized"));
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .with(csrf()))
        .andExpect(status().isNoContent());
    assertFalse(new AuthorizedContactService.Input(secret, true, true).toString().contains(secret));
    assertFalse(new AuthorizedContactService.Contact(secret).toString().contains(secret));
  }

  @Test
  void hiddenClosedOperatorAndContentRollback() throws Exception {
    long id = create();
    String op = account(), stranger = account();
    operators.provision(
        accountId(op),
        op,
        "GRANT",
        "Fixture",
        "Autorización ficticia",
        UUID.randomUUID().toString());
    contact(id, author, "+57 300 123 4567", true, true, 204);
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(stranger).roles("OPERATOR"))
                .with(csrf())
                .param("reason", "PROTECCION_DATOS"))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(op).roles("OPERATOR"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE citizen_publication SET visible=FALSE WHERE id=?", id);
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/publications/{id}", id)).andExpect(status().isNotFound());
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(op).roles("OPERATOR"))
                .with(csrf())
                .param("reason", "PROTECCION_DATOS"))
        .andExpect(status().isNoContent());
    jdbc.update("UPDATE citizen_publication SET visible=TRUE WHERE id=?", id);
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound());
    var edit = body();
    edit.put("operationalStatus", "DESAPARECIDA");
    var p = person();
    p.put("referenceName", "Referencia rectificada");
    edit.put("missingPerson", p);
    jdbc.execute(
        "ALTER TABLE citizen_publication_change ADD CONSTRAINT ra09_edit_rollback CHECK(action <> 'EDIT')");
    try {
      assertThrows(
          jakarta.servlet.ServletException.class,
          () ->
              mvc.perform(
                  patch("/api/my/publications/{id}", id)
                      .with(user(author))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(edit))));
    } finally {
      jdbc.execute("ALTER TABLE citizen_publication_change DROP CONSTRAINT ra09_edit_rollback");
    }
    assertEquals(
        "Persona de prueba",
        jdbc.queryForObject(
            "SELECT reference_name FROM citizen_publication WHERE id=?", String.class, id));
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(stranger))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isNotFound());
    for (var action :
        List.of(
            Map.of("action", "STATUS", "operationalStatus", "ENCONTRADA"),
            Map.of("action", "CLOSE", "operationalStatus", "CASO CERRADO"),
            Map.of("action", "REOPEN", "operationalStatus", "DESAPARECIDA"))) {
      var request = new HashMap<>(action);
      request.put("reason", "Decisión ficticia autorizada");
      mvc.perform(
              post("/api/operator/publications/{id}/actions", id)
                  .with(user(op).roles("OPERATOR"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(request)))
          .andExpect(status().isOk());
    }
    transition(id, "ENCONTRADA", "", 200);
    assertFalse(
        jdbc.queryForObject(
            "SELECT closed FROM citizen_publication WHERE id=?", Boolean.class, id));
    transition(id, "CASO CERRADO", "Cierre", 200);
    mvc.perform(
            patch("/api/my/publications/{id}", id)
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit)))
        .andExpect(status().isConflict());
    contact(id, author, "otro@example.invalid", true, true, 409);
    mvc.perform(get("/api/publications?category=MISSING_PERSON"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    mvc.perform(get("/api/publications?category=MISSING_PERSON&includeClosed=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
  }

  @Test
  void operatorCorrectionNeverRestoresContactAndRevocationIsImmediate() throws Exception {
    long id = create();
    String op = account();
    operators.provision(
        accountId(op),
        op,
        "GRANT",
        "Fixture",
        "Autorización ficticia",
        UUID.randomUUID().toString());
    contact(id, author, "privado@example.invalid", true, true, 204);
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .with(csrf()))
        .andExpect(status().isNoContent());
    var correction = body();
    correction.put("operationalStatus", "DESAPARECIDA");
    var p = person();
    p.put("ageRange", null);
    p.put("referenceName", "Caso rectificado");
    correction.put("missingPerson", p);
    mvc.perform(
            post("/api/operator/publications/{id}/actions", id)
                .with(user(op).roles("OPERATOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "action",
                            "CORRECT",
                            "reason",
                            "Corrección ficticia",
                            "correction",
                            correction))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(jsonPath("$.missingPerson.referenceName").value("Caso rectificado"));
    contact(id, author, "adulto@example.invalid", true, true, 204);
    operators.provision(
        accountId(op), op, "REVOKE", "Fixture", "Retiro ficticio", UUID.randomUUID().toString());
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(op).roles("OPERATOR"))
                .with(csrf())
                .param("reason", "PROTECCION_DATOS"))
        .andExpect(status().isNotFound());
    transition(id, "CASO CERRADO", "correo@example.invalid", 400);
    transition(id, "CASO CERRADO", "Cierre ficticio", 200);
    mvc.perform(
            delete("/api/my/publications/{id}/authorized-contact", id)
                .with(user(author))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/publications/{id}/authorized-contact", id))
        .andExpect(status().isNotFound());
    String audit =
        jdbc.queryForObject(
            "SELECT STRING_AGG(before_snapshot || after_snapshot, '') FROM citizen_publication_change WHERE publication_id=?",
            String.class,
            id);
    assertFalse(audit.contains("@example.invalid"));
  }

  @Test
  void locationIsApproximateAndContactNeverEntersCreationPayload() throws Exception {
    var request = body();
    request.put("latitude", 4.15049);
    request.put("longitude", -73.63049);
    request.put("contactValue", "ignorado@example.invalid");
    mvc.perform(
            post("/api/publications")
                .with(user(author))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.latitude").value(4.15))
        .andExpect(jsonPath("$.longitude").value(-73.63))
        .andExpect(jsonPath("$.contactValue").doesNotExist());
    assertEquals(
        0,
        jdbc.queryForObject("SELECT COUNT(*) FROM publication_authorized_contact", Integer.class));
  }
}

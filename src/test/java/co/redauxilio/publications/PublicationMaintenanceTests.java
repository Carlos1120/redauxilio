package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Pruebas HTTP+SQL reales: permisos, transiciones y auditoría persistida. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationMaintenanceTests {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  private final List<String> accounts = new ArrayList<>();

  @AfterEach
  void cleanOwnData() {
    for (String email : accounts) {
      jdbc.update(
          "DELETE FROM citizen_publication_change WHERE publication_id IN"
              + " (SELECT p.id FROM citizen_publication p JOIN citizen_account a"
              + " ON a.id = p.author_id WHERE a.email = ?)", email);
      jdbc.update(
          "DELETE FROM citizen_publication WHERE author_id IN"
              + " (SELECT id FROM citizen_account WHERE email = ?)", email);
      jdbc.update("DELETE FROM citizen_account WHERE email = ?", email);
    }
  }

  @Test
  void authenticatedAuthorCanEditButNotAnotherCitizenOrAnonymous() throws Exception {
    String owner = account();
    String stranger = account();
    long id = publish(owner, false);
    mvc.perform(get("/api/my/publications")).andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/my/publications/{id}", id).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(point("Nuevo")))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch("/api/my/publications/{id}", id).with(user(owner))
        .contentType(MediaType.APPLICATION_JSON).content(point("Nuevo")))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/my/publications/{id}", id).with(user(stranger)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(point("Nuevo")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(stranger)))
        .andExpect(status().isNotFound());
    mvc.perform(patch("/api/my/publications/{id}", id).with(user(owner)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(point("Nuevo")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Nuevo"));
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("EDIT"));
    mvc.perform(get("/api/my/publications").with(user(stranger)))
        .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    assertEquals(1, historyCount(id));
  }

  @Test
  void pointCloseReopenAndInvalidEditPreserveStateAndHistory() throws Exception {
    String owner = account();
    long id = publish(owner, false);
    mvc.perform(patch("/api/my/publications/{id}", id).with(user(owner)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(point("Nuevo")
            .replace("4.15", "100.0"))).andExpect(status().isBadRequest());
    assertEquals(0, historyCount(id));
    transition(owner, id, "CERRADO", "", 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true));
    mvc.perform(get("/api/publications").param("category", "ASSISTANCE"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]", id).doesNotExist());
    mvc.perform(get("/api/publications").param("includeClosed", "true"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == %s)]", id).exists());
    mvc.perform(patch("/api/my/publications/{id}", id).with(user(owner)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(point("Edición prohibida")))
        .andExpect(status().isConflict());
    transition(owner, id, "LLENO", "", 400);
    transition(owner, id, "DISPONIBLE", "", 400);
    assertEquals(1, historyCount(id));
    transition(owner, id, "DISPONIBLE", "Se confirmó reapertura", 200);
    mvc.perform(get("/api/publications/{id}", id))
        .andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(false));
    assertEquals(2, historyCount(id));
  }

  @Test
  void roadReopensWithReasonAndRejectsStateFromOtherCategory() throws Exception {
    String owner = account();
    long id = publish(owner, true);
    transition(owner, id, "HABILITADA", "", 200);
    transition(owner, id, "LLENO", "Motivo", 400);
    transition(owner, id, "PARCIALMENTE HABILITADA", "Volvió el daño", 200);
    mvc.perform(get("/api/my/publications/{id}/history", id).with(user(owner)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("CLOSE"))
        .andExpect(jsonPath("$[1].action").value("REOPEN"));
    assertEquals(2, historyCount(id));
  }

  private void transition(String userEmail, long id, String state, String reason, int expected)
      throws Exception {
    mvc.perform(post("/api/my/publications/{id}/status", id).with(user(userEmail)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(
            new PublicationMaintenanceController.TransitionRequest(state, reason))))
        .andExpect(status().is(expected));
  }

  private long publish(String email, boolean road) throws Exception {
    String body = road
        ? """
            {"category":"ROAD","title":"Vía prueba","description":"Daño reciente",
            "location":"Villavicencio","operationalStatus":"BLOQUEADA","impactType":"DERRUMBE",
            "latitude":4.15,"longitude":-73.63}
            """
        : point("Punto prueba");
    String response = mvc.perform(post("/api/publications").with(user(email)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("id").asLong();
  }

  private static String point(String title) {
    return """
        {"category":"ASSISTANCE","assistanceSubtype":"SHELTER","title":"%s",
        "description":"Apoyo comunitario","location":"Villavicencio",
        "operationalStatus":"DISPONIBLE","latitude":4.15,"longitude":-73.63}
        """.formatted(title);
  }

  private String account() {
    String email = "ra04-" + UUID.randomUUID() + "@example.test";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at)"
            + " VALUES (?, ?, ?, ?)", email, "Cuenta RA04", "hash-no-usado",
        Timestamp.from(Instant.now()));
    accounts.add(email);
    return email;
  }

  private int historyCount(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM citizen_publication_change WHERE publication_id = ?",
        Integer.class, id);
  }
}

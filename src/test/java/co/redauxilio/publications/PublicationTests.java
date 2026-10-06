package co.redauxilio.publications;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas del contrato HTTP con el contexto real de Spring y peticiones simuladas por MockMvc.
 * Comprueban JSON, estados y recursos; no abren Chrome ni prueban interacción visual o rendimiento.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationTests {
  @Autowired private MockMvc mvc;

  @Test
  void returnsPublicDataWithoutLogin() throws Exception {
    mvc.perform(get("/api/publications"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSISTANCE", "ROAD", "MISSING_PERSON", "HELP_REQUEST"})
  void filtersEachCategory(String category) throws Exception {
    mvc.perform(get("/api/publications").param("category", category))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].category", is(category)));
  }

  @Test
  void normalizesCategory() throws Exception {
    mvc.perform(get("/api/publications").param("category", " road "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].category", is("ROAD")));
  }

  @Test
  void rejectsUnknownCategory() throws Exception {
    mvc.perform(get("/api/publications").param("category", "OTHER"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void blankCategoryReturnsAll() throws Exception {
    mvc.perform(get("/api/publications").param("category", " "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)));
  }

  @Test
  void combinesFiltersAndArea() throws Exception {
    mvc.perform(
            get("/api/publications")
                .param("category", "ROAD")
                .param("operationalStatus", "Bloqueada")
                .param("confidenceLevel", "REPORTADA")
                .param("south", "4.13")
                .param("west", "-73.64")
                .param("north", "4.15")
                .param("east", "-73.61"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id", is(2)));
  }

  @Test
  void closedCasesRequireExplicitFilter() throws Exception {
    mvc.perform(get("/api/publications").param("operationalStatus", "Cerrado"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    mvc.perform(get("/api/publications").param("includeClosed", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(5)));
    mvc.perform(
            get("/api/publications")
                .param("includeClosed", "true")
                .param("operationalStatus", "Cerrado"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is(5)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"6", "999"})
  void hiddenAndMissingDetailsReturnNotFound(String id) throws Exception {
    mvc.perform(get("/api/publications/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void detailsMatchTheSelectedReport() throws Exception {
    mvc.perform(get("/api/publications/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category", is("ROAD")))
        .andExpect(jsonPath("$.latitude", is(4.14)))
        .andExpect(jsonPath("$.description").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  @Test
  void rejectsInvalidFiltersAndBounds() throws Exception {
    for (String field : new String[] {"operationalStatus", "confidenceLevel"}) {
      mvc.perform(get("/api/publications").param(field, "INVALID"))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/publications").param("south", "4")).andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/publications")
                .param("south", "5")
                .param("west", "-74")
                .param("north", "4")
                .param("east", "-73"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/publications")
                .param("south", "NaN")
                .param("west", "-74")
                .param("north", "5")
                .param("east", "-73"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyAreaReturnsNoReports() throws Exception {
    mvc.perform(
            get("/api/publications")
                .param("south", "0")
                .param("west", "0")
                .param("north", "1")
                .param("east", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void rendersHome() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("index"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "Todos los reportes de esta versión son ficticios")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/app.js", "/map.js", "/app.css", "/sw.js", "/manifest.webmanifest", "/icon.svg"})
  void servesPwaAssets(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isOk());
  }
}

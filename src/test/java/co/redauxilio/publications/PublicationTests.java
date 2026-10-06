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
  @ValueSource(strings = {"/app.js", "/app.css", "/sw.js", "/manifest.webmanifest", "/icon.svg"})
  void servesPwaAssets(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isOk());
  }
}

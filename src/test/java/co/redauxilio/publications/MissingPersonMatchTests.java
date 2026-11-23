package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba la simulación con imágenes sintéticas creadas exclusivamente durante la prueba. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:missing-match;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.sql.init.mode=always"
    })
@AutoConfigureMockMvc
class MissingPersonMatchTests {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MissingPersonMatchService service;
  private byte[] prepared;
  private byte[] comparison;

  @BeforeEach
  void fixture() throws Exception {
    jdbc.update("DELETE FROM simulated_missing_match_input");
    jdbc.update("DELETE FROM simulated_missing_photo");
    jdbc.update("DELETE FROM simulated_missing_candidate");
    prepared = png(Color.CYAN);
    comparison = png(Color.GREEN);
    PublicationEvidenceService.ValidatedImage image =
        PublicationEvidenceService.validate("prepared.png", "image/png", prepared);
    PublicationEvidenceService.ValidatedImage comparisonImage =
        PublicationEvidenceService.validate("comparison.png", "image/png", comparison);
    service.upsertFixture(
        new MissingPersonMatchService.Fixture(
            "CASE_TEST",
            "Persona ficticia de prueba",
            "25–35 años",
            "Caso sintético creado por la prueba.",
            92,
            List.of("unused.png"),
            List.of("comparison.png")),
        List.of(image),
        List.of(comparisonImage));
  }

  @Test
  void exposesPreparedCatalogAndPhotoWithoutCache() throws Exception {
    mvc.perform(get("/api/missing-person-match/candidates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].referenceName").value("Persona ficticia de prueba"))
        .andExpect(jsonPath("$[0].simulatedScore").value(92))
        .andExpect(jsonPath("$[0].photoCount").value(1))
        .andExpect(
            jsonPath("$[0].comparisonPhotoUrl")
                .value(org.hamcrest.Matchers.endsWith("/comparison-photo")));
    long id =
        jdbc.queryForObject(
            "SELECT id FROM simulated_missing_candidate WHERE code='CASE_TEST'", Long.class);
    mvc.perform(get("/api/missing-person-match/candidates/{id}/photo", id))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Type", "image/png"));
    mvc.perform(get("/api/missing-person-match/candidates/{id}/comparison-photo", id))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
        .andExpect(header().string("Content-Type", "image/png"));
  }

  @Test
  void requiresSessionAndReturnsPredeterminedMatchWithoutPersistingUpload() throws Exception {
    MockMultipartFile photo = new MockMultipartFile("files", "match.png", "image/png", comparison);
    mvc.perform(multipart("/api/missing-person-match/simulate").file(photo).with(csrf()))
        .andExpect(status().isUnauthorized());
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM simulated_missing_photo", Integer.class);
    int comparisonBefore =
        jdbc.queryForObject("SELECT COUNT(*) FROM simulated_missing_match_input", Integer.class);
    mvc.perform(
            multipart("/api/missing-person-match/simulate")
                .file(photo)
                .with(user("citizen@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matched").value(true))
        .andExpect(jsonPath("$.candidate.simulatedScore").value(92))
        .andExpect(jsonPath("$.comparedPhotos").value(1))
        .andExpect(
            jsonPath("$.message")
                .value(org.hamcrest.Matchers.containsString("no confirma identidad")));
    assertEquals(
        before, jdbc.queryForObject("SELECT COUNT(*) FROM simulated_missing_photo", Integer.class));
    assertEquals(
        comparisonBefore,
        jdbc.queryForObject("SELECT COUNT(*) FROM simulated_missing_match_input", Integer.class));
  }

  /** La foto descargada desde el catálogo debe seguir coincidiendo al volver a cargarla. */
  @Test
  void downloadedJpegComparisonPhotoMatchesAfterUpload() throws Exception {
    byte[] original;
    try (var resource = getClass().getResourceAsStream("/demo-match/jesus-pena-comparison.jpeg")) {
      org.junit.jupiter.api.Assertions.assertNotNull(resource);
      original = resource.readAllBytes();
    }
    PublicationEvidenceService.ValidatedImage jpeg =
        PublicationEvidenceService.validate("comparison.jpeg", "image/jpeg", original);
    long id =
        service.upsertFixture(
            new MissingPersonMatchService.Fixture(
                "JPEG_TEST",
                "Caso JPEG",
                null,
                "Entrada de prueba.",
                90,
                List.of("unused.png"),
                List.of("comparison.jpeg")),
            List.of(PublicationEvidenceService.validate("prepared.png", "image/png", prepared)),
            List.of(jpeg));
    byte[] download =
        mvc.perform(get("/api/missing-person-match/candidates/{id}/comparison-photo", id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    mvc.perform(
            multipart("/api/missing-person-match/simulate")
                .file(new MockMultipartFile("files", "comparison.jpeg", "image/jpeg", download))
                .with(user("citizen@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matched").value(true));
  }

  /** Una foto de referencia preparada también activa el caso sin inferir otros rostros. */
  @Test
  void preparedReferencePhotoMatchesAfterUpload() throws Exception {
    mvc.perform(
            multipart("/api/missing-person-match/simulate")
                .file(new MockMultipartFile("files", "prepared.png", "image/png", prepared))
                .with(user("citizen@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matched").value(true))
        .andExpect(jsonPath("$.candidate.referenceName").value("Persona ficticia de prueba"));
  }

  @Test
  void returnsNoMatchAndRejectsMoreThanThreePhotos() throws Exception {
    MockMultipartFile different =
        new MockMultipartFile("files", "different.png", "image/png", png(Color.MAGENTA));
    mvc.perform(
            multipart("/api/missing-person-match/simulate")
                .file(different)
                .with(user("citizen@example.invalid"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matched").value(false))
        .andExpect(jsonPath("$.candidate").doesNotExist());
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request =
        multipart("/api/missing-person-match/simulate");
    request.with(user("citizen@example.invalid")).with(csrf());
    for (int index = 0; index < 4; index++)
      request.file(new MockMultipartFile("files", "photo.png", "image/png", png(new Color(index))));
    mvc.perform(request).andExpect(status().isBadRequest());
  }

  private static byte[] png(Color color) throws Exception {
    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < image.getWidth(); x++)
      for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, color.getRGB());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}

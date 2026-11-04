package co.redauxilio.publications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Comprueba evidencias por HTTP/SQL; usa H2 por defecto o PostgreSQL temporal con RA12_TEST_DB_*.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicationEvidenceTests {
  /** Selecciona la base explícita de pruebas, manteniendo H2 como opción predeterminada de CI. */
  @DynamicPropertySource
  static void testDatabase(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "RA12_TEST_DB_URL",
                    "jdbc:h2:mem:ra12evidence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("RA12_TEST_DB_USERNAME", "sa"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("RA12_TEST_DB_PASSWORD", ""));
    properties.add("spring.sql.init.mode", () -> "always");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  private String owner;
  private long publicationId;

  @BeforeEach
  void createFixture() throws Exception {
    owner = account();
    String response =
        mvc.perform(
                post("/api/publications")
                    .with(user(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Refugio de prueba\",\"description\":\"Descripción de prueba\",\"location\":\"Sector público\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":4.15,\"longitude\":-73.63}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode created = json.readTree(response);
    publicationId = created.path("id").asLong();
  }

  @Test
  void uploadsListsAndServesSanitizedPngAndOwnerCanRemove() throws Exception {
    mvc.perform(upload(png(), png()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].contentType").value("image/png"))
        .andExpect(jsonPath("$[0].url").exists());
    String url =
        mvc.perform(get("/api/publications/{id}/evidence", publicationId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long evidenceId = json.readTree(url).get(0).path("id").asLong();
    mvc.perform(get("/api/publications/{id}/evidence/{imageId}", publicationId, evidenceId))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/my/publications/{id}/evidence/{imageId}", publicationId, evidenceId)
                .with(user(owner))
                .with(csrf()))
        .andExpect(status().isNoContent());
    long secondId = json.readTree(url).get(1).path("id").asLong();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/my/publications/{id}/evidence/{imageId}", publicationId, secondId)
                .with(user(owner))
                .with(csrf()))
        .andExpect(status().isNoContent());
    assertEquals(0, countForPublication());
  }

  @Test
  void rejectsSpoofedOversizeAndTooManyFilesWithoutPartialInsert() throws Exception {
    mvc.perform(
            upload(
                new MockMultipartFile(
                    "files", "spoof.png", "image/png", "<html>bad</html>".getBytes())))
        .andExpect(status().isBadRequest());
    mvc.perform(
            upload(
                new MockMultipartFile(
                    "files", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
        .andExpect(status().isBadRequest());
    mvc.perform(upload(png(), png(), png(), png())).andExpect(status().isBadRequest());
    assertEquals(0, countForPublication());
  }

  @Test
  void rejectsAWebpFrameWithDimensionsButNoDecodableBitstream() throws Exception {
    mvc.perform(
            upload(
                new MockMultipartFile(
                    "files", "truncated.webp", "image/webp", truncatedLossyWebp())))
        .andExpect(status().isBadRequest());
    assertEquals(0, countForPublication());
  }

  @Test
  void acceptsAWebpThatTheDecoderCanRead() throws Exception {
    mvc.perform(upload(webp())).andExpect(status().isCreated());
    assertEquals(1, countForPublication());
  }

  @Test
  void rejectsAnImageWhoseDeclaredMimeDoesNotMatchItsContent() throws Exception {
    byte[] png = png().getBytes();
    mvc.perform(upload(new MockMultipartFile("files", "evidence.png", "image/jpeg", png)))
        .andExpect(status().isBadRequest());
    assertEquals(0, countForPublication());
    MockMultipartFile wrongExtension =
        new MockMultipartFile("files", "evidence.jpg", "image/png", png().getBytes());
    mvc.perform(upload(wrongExtension)).andExpect(status().isBadRequest());
    assertEquals(0, countForPublication());
  }

  @Test
  void onlyOwnerCanWriteAndHiddenPublicationHidesImages() throws Exception {
    String stranger = account();
    mvc.perform(uploadAs(stranger, png())).andExpect(status().isNotFound());
    mvc.perform(uploadAs(null, png())).andExpect(status().isUnauthorized());
    mvc.perform(
            multipart("/api/my/publications/{id}/evidence", publicationId)
                .file(png())
                .with(user(owner)))
        .andExpect(status().isForbidden());
    assertEquals(0, countForPublication());
  }

  @Test
  void hiddenAndClosedPublicationsProtectEvidence() throws Exception {
    mvc.perform(upload(png())).andExpect(status().isCreated());
    long imageId =
        jdbc.queryForObject(
            "SELECT id FROM publication_evidence WHERE publication_id = ?",
            Long.class,
            publicationId);
    jdbc.update("UPDATE citizen_publication SET visible = FALSE WHERE id = ?", publicationId);
    mvc.perform(get("/api/publications/{id}/evidence", publicationId))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/publications/{id}/evidence/{imageId}", publicationId, imageId))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE citizen_publication SET visible = TRUE WHERE id = ?", publicationId);
    mvc.perform(get("/api/publications/{id}/evidence", publicationId)).andExpect(status().isOk());
    jdbc.update(
        "UPDATE citizen_publication SET visible = TRUE, closed = TRUE WHERE id = ?", publicationId);
    mvc.perform(upload(png())).andExpect(status().isConflict());
    assertEquals(1, countForPublication());
  }

  @Test
  void invalidImageInBatchDoesNotInsertValidEarlierFile() throws Exception {
    mvc.perform(
            upload(png(), new MockMultipartFile("files", "bad.png", "image/png", "bad".getBytes())))
        .andExpect(status().isBadRequest());
    assertEquals(0, countForPublication());
  }

  @Test
  void createsPublicationAndEvidenceAtomically() throws Exception {
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class);
    MockMultipartFile publication =
        new MockMultipartFile(
            "publication",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Alta con foto\",\"description\":\"Descripción\",\"location\":\"Sector\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":4.15,\"longitude\":-73.63}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String result =
        mvc.perform(
                multipart("/api/publications/with-evidence")
                    .file(publication)
                    .file(png())
                    .with(user(owner))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long createdId = json.readTree(result).path("id").asLong();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_evidence WHERE publication_id = ?",
            Integer.class,
            createdId));
    assertEquals(
        before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class));

    publication =
        new MockMultipartFile(
            "publication",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            pointRequest().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    int countBeforeFailure =
        jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class);
    mvc.perform(
            multipart("/api/publications/with-evidence")
                .file(publication)
                .file(
                    new MockMultipartFile(
                        "files", "fake.png", "image/png", "not an image".getBytes()))
                .with(user(owner))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(
        countBeforeFailure,
        jdbc.queryForObject("SELECT COUNT(*) FROM citizen_publication", Integer.class));
  }

  private MockHttpServletRequestBuilder upload(MockMultipartFile... files) {
    return uploadAs(owner, files);
  }

  private MockHttpServletRequestBuilder uploadAs(String account, MockMultipartFile... files) {
    var request = multipart("/api/my/publications/{id}/evidence", publicationId);
    for (MockMultipartFile file : files) request.file(file);
    if (account != null) request.with(user(account));
    return request.with(csrf());
  }

  private String account() {
    String email = "ra12-" + UUID.randomUUID() + "@example.test";
    jdbc.update(
        "INSERT INTO citizen_account (email, display_name, password_hash, created_at) VALUES (?, 'Test', 'unused', ?)",
        email,
        Timestamp.from(Instant.now()));
    return email;
  }

  private int countForPublication() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM publication_evidence WHERE publication_id = ?",
        Integer.class,
        publicationId);
  }

  private static MockMultipartFile png() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0x336699);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return new MockMultipartFile("files", "evidence.png", "image/png", bytes.toByteArray());
  }

  private static MockMultipartFile webp() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0x336699);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "webp", bytes))
      throw new IllegalStateException("WebP writer missing");
    return new MockMultipartFile("files", "evidence.webp", "image/webp", bytes.toByteArray());
  }

  private static byte[] truncatedLossyWebp() {
    byte[] bytes = new byte[30];
    System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 0, 4);
    bytes[4] = (byte) (bytes.length - 8);
    System.arraycopy("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 4);
    System.arraycopy("VP8 ".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 12, 4);
    bytes[16] = 10;
    bytes[23] = (byte) 0x9d;
    bytes[24] = 0x01;
    bytes[25] = 0x2a;
    bytes[26] = 1;
    bytes[28] = 1;
    return bytes;
  }

  private static String pointRequest() {
    return "{\"category\":\"ASSISTANCE\",\"assistanceSubtype\":\"SHELTER\",\"title\":\"Alta con foto\",\"description\":\"Descripción\",\"location\":\"Sector\",\"operationalStatus\":\"DISPONIBLE\",\"latitude\":4.15,\"longitude\":-73.63}";
  }
}

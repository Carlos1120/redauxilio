package co.redauxilio.publications;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Valida, asocia y sirve evidencias sin usar el nombre del archivo como ruta ni como contenido. */
@Service
class PublicationEvidenceService {
  static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
  static final int MAX_IMAGES = 3;
  private static final long MAX_PIXELS = 20_000_000L;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  PublicationEvidenceService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Persiste un lote completo o ninguno; cada parte se valida por firma y decodificación real. */
  @Transactional
  List<Evidence> add(long publicationId, String email, List<MultipartFile> files) {
    if (files == null || files.isEmpty() || files.size() > MAX_IMAGES)
      throw invalid("Adjunta entre una y tres imágenes.");
    lockOwnedPublication(publicationId, email, true);
    int existing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM publication_evidence WHERE publication_id = ?",
            Integer.class,
            publicationId);
    if (existing + files.size() > MAX_IMAGES)
      throw invalid("Una publicación admite máximo tres imágenes.");
    List<ValidatedImage> validated =
        files.stream().map(PublicationEvidenceService::validate).toList();
    for (ValidatedImage image : validated)
      jdbc.update(
          "INSERT INTO publication_evidence (publication_id, content_type, image_bytes, added_at) VALUES (?, ?, ?, ?)",
          publicationId,
          image.contentType(),
          image.bytes(),
          Timestamp.from(clock.instant()));
    return list(publicationId);
  }

  /** Retira una evidencia solo si pertenece al autor y la publicación admite cambios. */
  @Transactional
  void remove(long publicationId, long evidenceId, String email) {
    lockOwnedPublication(publicationId, email, true);
    int removed =
        jdbc.update(
            "DELETE FROM publication_evidence WHERE id = ? AND publication_id = ?",
            evidenceId,
            publicationId);
    if (removed == 0) throw notFound();
  }

  /** Lista metadatos públicos únicamente para publicaciones visibles. */
  @Transactional(readOnly = true)
  List<Evidence> listPublic(long publicationId) {
    if (jdbc.query(
            "SELECT id FROM citizen_publication WHERE id = ? AND visible = TRUE",
            (row, index) -> row.getLong(1),
            publicationId)
        .isEmpty()) throw notFound();
    return list(publicationId);
  }

  /** Lee bytes solo tras comprobar que la publicación padre sigue visible. */
  @Transactional(readOnly = true)
  Image image(long publicationId, long evidenceId) {
    return jdbc
        .query(
            "SELECT e.content_type, e.image_bytes FROM publication_evidence e JOIN citizen_publication p ON p.id = e.publication_id WHERE e.publication_id = ? AND e.id = ? AND p.visible = TRUE",
            (row, index) -> new Image(row.getString(1), row.getBytes(2)),
            publicationId,
            evidenceId)
        .stream()
        .findFirst()
        .orElseThrow(PublicationEvidenceService::notFound);
  }

  private List<Evidence> list(long publicationId) {
    return jdbc.query(
        "SELECT id, content_type, added_at FROM publication_evidence WHERE publication_id = ? ORDER BY id",
        (row, index) ->
            new Evidence(
                row.getLong(1),
                row.getString(2),
                row.getTimestamp(3).toInstant().toString(),
                "/api/publications/" + publicationId + "/evidence/" + row.getLong(1)),
        publicationId);
  }

  private void lockOwnedPublication(long id, String email, boolean requireOpen) {
    var rows =
        jdbc.query(
            "SELECT p.closed FROM citizen_publication p JOIN citizen_account a ON a.id = p.author_id WHERE p.id = ? AND p.visible = TRUE AND a.email = ? FOR UPDATE",
            (row, index) -> row.getBoolean(1),
            id,
            email);
    if (rows.isEmpty()) throw notFound();
    if (requireOpen && rows.get(0))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Reabre la publicación antes de cambiar imágenes.");
  }

  private static ValidatedImage validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw invalid("El archivo está vacío.");
    if (file.getSize() > MAX_IMAGE_BYTES) throw invalid("Cada imagen debe pesar máximo 5 MB.");
    try {
      byte[] bytes = file.getBytes();
      if (isPng(bytes)) return decodeAndReencode(bytes, "png", "image/png");
      if (isJpeg(bytes)) return decodeAndReencode(bytes, "jpeg", "image/jpeg");
      if (isWebp(bytes)) return validateWebp(bytes);
      throw invalid("El contenido no es una imagen JPEG, PNG o WebP válida.");
    } catch (IOException failure) {
      throw invalid("No se pudo leer una imagen válida.");
    }
  }

  private static ValidatedImage decodeAndReencode(byte[] bytes, String format, String contentType)
      throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) throw invalid("La imagen no se puede decodificar.");
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid("La imagen no se puede decodificar.");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
          throw invalid("La imagen tiene dimensiones demasiado grandes.");
        BufferedImage decoded = reader.read(0);
        if (decoded == null) throw invalid("La imagen está incompleta.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(decoded, format, output))
          throw invalid("Formato de imagen no disponible.");
        byte[] sanitized = output.toByteArray();
        if (sanitized.length > MAX_IMAGE_BYTES) throw invalid("La imagen procesada excede 5 MB.");
        return new ValidatedImage(contentType, sanitized);
      } finally {
        reader.dispose();
      }
    }
  }

  /** Comprueba el contenedor RIFF y un chunk de imagen; no confía en el MIME ni la extensión. */
  private static ValidatedImage validateWebp(byte[] bytes) {
    if (bytes.length < 30 || littleEndianInt(bytes, 4) != bytes.length - 8)
      throw invalid("El contenedor WebP está incompleto.");
    int offset = 12;
    boolean imageChunk = false;
    while (offset + 8 <= bytes.length) {
      String type = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
      long size = Integer.toUnsignedLong(littleEndianInt(bytes, offset + 4));
      long end = (long) offset + 8 + size + (size & 1);
      if (end > bytes.length) throw invalid("El contenedor WebP está incompleto.");
      if (type.equals("VP8 ") && size >= 10) {
        int p = offset + 8;
        if ((bytes[p + 3] & 0xff) != 0x9d
            || (bytes[p + 4] & 0xff) != 0x01
            || (bytes[p + 5] & 0xff) != 0x2a) throw invalid("La trama WebP no es válida.");
        checkPixels(
            ((bytes[p + 7] & 0x3f) << 8) | (bytes[p + 6] & 0xff),
            ((bytes[p + 9] & 0x3f) << 8) | (bytes[p + 8] & 0xff));
        imageChunk = true;
      } else if (type.equals("VP8L") && size >= 5) {
        int p = offset + 8;
        if ((bytes[p] & 0xff) != 0x2f) throw invalid("La trama WebP no es válida.");
        int width = 1 + (bytes[p + 1] & 0xff) + ((bytes[p + 2] & 0x3f) << 8);
        int height =
            1
                + ((bytes[p + 2] & 0xc0) >>> 6)
                + ((bytes[p + 3] & 0xff) << 2)
                + ((bytes[p + 4] & 0x0f) << 10);
        checkPixels(width, height);
        imageChunk = true;
      } else if (type.equals("VP8X") && size >= 10) {
        int p = offset + 8;
        int width =
            1
                + (bytes[p + 4] & 0xff)
                + ((bytes[p + 5] & 0xff) << 8)
                + ((bytes[p + 6] & 0xff) << 16);
        int height =
            1
                + (bytes[p + 7] & 0xff)
                + ((bytes[p + 8] & 0xff) << 8)
                + ((bytes[p + 9] & 0xff) << 16);
        checkPixels(width, height);
      }
      offset = (int) end;
    }
    if (offset != bytes.length || !imageChunk)
      throw invalid("El WebP no contiene una imagen completa.");
    // Las imágenes WebP se aceptan solo con contenedor estructural consistente; el tamaño total
    // limita el recurso y se omiten metadatos EXIF/XMP al reconstruir los chunks.
    ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length);
    output.writeBytes("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    output.write(new byte[4], 0, 4);
    output.writeBytes("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    for (int chunkOffset = 12; chunkOffset < bytes.length; ) {
      String type = new String(bytes, chunkOffset, 4, java.nio.charset.StandardCharsets.US_ASCII);
      int size = littleEndianInt(bytes, chunkOffset + 4);
      int end = chunkOffset + 8 + size + (size & 1);
      if (!type.equals("EXIF") && !type.equals("XMP ")) {
        if (type.equals("VP8X")) bytes[chunkOffset + 8] = (byte) (bytes[chunkOffset + 8] & ~0x0c);
        output.write(bytes, chunkOffset, end - chunkOffset);
      }
      chunkOffset = end;
    }
    byte[] sanitized = output.toByteArray();
    putLittleEndianInt(sanitized, 4, sanitized.length - 8);
    return new ValidatedImage("image/webp", sanitized);
  }

  private static boolean isPng(byte[] b) {
    byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    return b.length >= 24 && startsWith(b, signature);
  }

  private static boolean isJpeg(byte[] b) {
    return b.length >= 4
        && (b[0] & 0xff) == 0xff
        && (b[1] & 0xff) == 0xd8
        && (b[2] & 0xff) == 0xff
        && b[b.length - 2] == (byte) 0xff
        && (b[b.length - 1] & 0xff) == 0xd9;
  }

  private static boolean isWebp(byte[] b) {
    return b.length >= 16
        && startsWith(b, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
  }

  private static boolean startsWith(byte[] b, byte[] prefix) {
    for (int i = 0; i < prefix.length; i++) if (b[i] != prefix[i]) return false;
    return true;
  }

  private static void checkPixels(int width, int height) {
    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
      throw invalid("La imagen tiene dimensiones demasiado grandes.");
  }

  private static int littleEndianInt(byte[] b, int p) {
    return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | (b[p + 3] << 24);
  }

  private static void putLittleEndianInt(byte[] b, int p, int value) {
    b[p] = (byte) value;
    b[p + 1] = (byte) (value >>> 8);
    b[p + 2] = (byte) (value >>> 16);
    b[p + 3] = (byte) (value >>> 24);
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidencia no disponible.");
  }

  record Evidence(long id, String contentType, String addedAt, String url) {}

  record Image(String contentType, byte[] bytes) {}

  private record ValidatedImage(String contentType, byte[] bytes) {}
}

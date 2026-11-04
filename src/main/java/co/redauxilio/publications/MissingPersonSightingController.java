package co.redauxilio.publications;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Consulta el historial público y registra avistamientos con sesión y protección CSRF. */
@RestController
class MissingPersonSightingController {
  private final MissingPersonSightingService sightings;

  MissingPersonSightingController(MissingPersonSightingService sightings) {
    this.sightings = sightings;
  }

  @GetMapping("/api/publications/{publicationId}/sightings")
  ResponseEntity<List<MissingPersonSighting>> list(@PathVariable long publicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(sightings.listPublic(publicationId));
  }

  @GetMapping("/api/publications/{publicationId}/sightings/{sightingId}/evidence/{evidenceId}")
  ResponseEntity<byte[]> image(
      @PathVariable long publicationId,
      @PathVariable long sightingId,
      @PathVariable long evidenceId) {
    PublicationEvidenceService.Image image = sightings.image(publicationId, sightingId, evidenceId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PostMapping(
      path = "/api/my/publications/{publicationId}/sightings",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<MissingPersonSighting> create(
      @AuthenticationPrincipal UserDetails citizen,
      @PathVariable long publicationId,
      @RequestPart("sighting") MissingPersonSightingRequest request,
      @RequestPart(value = "files", required = false) List<MultipartFile> files) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(sightings.create(publicationId, citizen.getUsername(), request, files));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> knownError(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                error.getReason() == null ? "Avistamiento no disponible." : error.getReason()));
  }
}

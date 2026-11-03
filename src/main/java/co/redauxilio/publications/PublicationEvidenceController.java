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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Rutas públicas de lectura y rutas autenticadas de autoría para evidencias fotográficas. */
@RestController
class PublicationEvidenceController {
  private final PublicationEvidenceService evidence;

  PublicationEvidenceController(PublicationEvidenceService evidence) {
    this.evidence = evidence;
  }

  @GetMapping("/api/publications/{publicationId}/evidence")
  ResponseEntity<List<PublicationEvidenceService.Evidence>> list(@PathVariable long publicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(evidence.listPublic(publicationId));
  }

  @GetMapping("/api/publications/{publicationId}/evidence/{evidenceId}")
  ResponseEntity<byte[]> image(@PathVariable long publicationId, @PathVariable long evidenceId) {
    PublicationEvidenceService.Image image = evidence.image(publicationId, evidenceId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PostMapping(
      path = "/api/my/publications/{publicationId}/evidence",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<List<PublicationEvidenceService.Evidence>> add(
      @AuthenticationPrincipal UserDetails citizen,
      @PathVariable long publicationId,
      @RequestPart("files") List<MultipartFile> files) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(evidence.add(publicationId, citizen.getUsername(), files));
  }

  @DeleteMapping("/api/my/publications/{publicationId}/evidence/{evidenceId}")
  ResponseEntity<Void> remove(
      @AuthenticationPrincipal UserDetails citizen,
      @PathVariable long publicationId,
      @PathVariable long evidenceId) {
    evidence.remove(publicationId, evidenceId, citizen.getUsername());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> knownError(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                error.getReason() == null ? "Evidencia no disponible." : error.getReason()));
  }
}

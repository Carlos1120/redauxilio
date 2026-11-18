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

/** Expone la comparación guiada de fotografías preparada para la exposición. */
@RestController
class MissingPersonMatchController {
  private final MissingPersonMatchService matches;

  MissingPersonMatchController(MissingPersonMatchService matches) {
    this.matches = matches;
  }

  @GetMapping("/api/missing-person-match/candidates")
  ResponseEntity<List<MissingPersonMatchService.Candidate>> candidates() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(matches.candidates());
  }

  @GetMapping("/api/missing-person-match/candidates/{candidateId}/photo")
  ResponseEntity<byte[]> photo(@PathVariable long candidateId) {
    PublicationEvidenceService.Image image = matches.photo(candidateId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @GetMapping("/api/missing-person-match/candidates/{candidateId}/comparison-photo")
  ResponseEntity<byte[]> comparisonPhoto(@PathVariable long candidateId) {
    PublicationEvidenceService.Image image = matches.comparisonPhoto(candidateId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=buscar-coincidencia.jpg")
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PostMapping(
      path = "/api/missing-person-match/simulate",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<MissingPersonMatchService.MatchResult> simulate(
      @AuthenticationPrincipal UserDetails citizen,
      @RequestPart("files") List<MultipartFile> files) {
    if (citizen == null)
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Inicia sesión para comparar fotos.");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(matches.simulate(files));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> knownError(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                error.getReason() == null ? "Comparación no disponible." : error.getReason()));
  }
}

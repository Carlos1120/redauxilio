package co.redauxilio.publications;

import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Preconsulta privada POST para no colocar datos del borrador en URLs/caches publicas. */
@RestController
class DuplicatePublicationController {
  private final DuplicatePublicationService duplicates;

  DuplicatePublicationController(DuplicatePublicationService duplicates) {
    this.duplicates = duplicates;
  }

  @PostMapping("/api/my/publications/possible-duplicates")
  ResponseEntity<List<DuplicatePublicationService.Candidate>> find(
      @RequestBody CreatePublicationRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(duplicates.find(request));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", e.getReason() == null ? "Consulta no disponible" : e.getReason()));
  }
}

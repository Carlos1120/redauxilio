package co.redauxilio.publications;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** POST privado con CSRF; devuelve solo datos públicos, nunca motivo, denunciante o auditoría. */
@RestController
class PublicationContentReportController {
  private final PublicationContentReportService reports;
  private final PublicationService publications;

  PublicationContentReportController(
      PublicationContentReportService reports, PublicationService publications) {
    this.reports = reports;
    this.publications = publications;
  }

  @PostMapping("/api/publications/{id}/content-reports")
  ResponseEntity<PublicationService.Publication> report(
      @PathVariable long id,
      @AuthenticationPrincipal UserDetails citizen,
      @RequestBody ContentReportRequest request) {
    reports.report(id, citizen.getUsername(), request.reason());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(publications.findPublication(id));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                error.getReason() == null
                    ? "No se pudo reportar el contenido"
                    : error.getReason()));
  }

  record ContentReportRequest(String reason) {}
}

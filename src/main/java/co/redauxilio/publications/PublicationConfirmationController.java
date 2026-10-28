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

/** Escritura privada con CSRF: la respuesta devuelve solo el mismo contrato público de consulta. */
@RestController
class PublicationConfirmationController {
  private final PublicationConfirmationService confirmations;
  private final PublicationService publications;

  PublicationConfirmationController(
      PublicationConfirmationService confirmations, PublicationService publications) {
    this.confirmations = confirmations;
    this.publications = publications;
  }

  @PostMapping("/api/publications/{id}/confirmations")
  ResponseEntity<PublicationService.Publication> confirm(
      @PathVariable long id,
      @AuthenticationPrincipal UserDetails citizen,
      @RequestBody ConfirmationRequest request) {
    if (request.version() == null || request.version() < 1)
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Indica la versión consultada");
    confirmations.confirm(id, citizen.getUsername(), request.version());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(publications.findPublication(id));
  }

  record ConfirmationRequest(Long version) {}

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message", error.getReason() == null ? "No se pudo confirmar" : error.getReason()));
  }
}

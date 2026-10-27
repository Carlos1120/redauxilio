package co.redauxilio.publications;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** API privada para el autor: nunca usa ID de cuenta ni permisos enviados por el navegador. */
@RestController
@RequestMapping("/api/my/publications")
class PublicationMaintenanceController {
  private final PublicationMaintenanceService service;

  PublicationMaintenanceController(PublicationMaintenanceService service) {
    this.service = service;
  }

  @GetMapping
  ResponseEntity<List<PublicationService.Publication>> list(
      @AuthenticationPrincipal UserDetails citizen) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.listOwn(citizen.getUsername()));
  }

  @PatchMapping("/{id}")
  ResponseEntity<PublicationService.Publication> edit(
      @AuthenticationPrincipal UserDetails citizen, @PathVariable long id,
      @RequestBody CreatePublicationRequest request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.edit(id, citizen.getUsername(), request));
  }

  @PostMapping("/{id}/status")
  ResponseEntity<PublicationService.Publication> status(
      @AuthenticationPrincipal UserDetails citizen, @PathVariable long id,
      @RequestBody TransitionRequest request) {
    if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.transition(
            id, citizen.getUsername(), request.operationalStatus(), request.reason()));
  }

  @GetMapping("/{id}/history")
  ResponseEntity<List<PublicationMaintenanceService.Change>> history(
      @AuthenticationPrincipal UserDetails citizen, @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(service.history(id, citizen.getUsername()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> knownError(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", error.getReason() == null
            ? "Operación no disponible" : error.getReason()));
  }

  record TransitionRequest(String operationalStatus, String reason) {}
}

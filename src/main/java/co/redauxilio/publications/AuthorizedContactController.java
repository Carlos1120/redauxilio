package co.redauxilio.publications;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Ruta separada no cacheable: no adjunta contacto a lista, detalle, edición o auditoría. */
@RestController
class AuthorizedContactController {
  private final AuthorizedContactService contacts;

  AuthorizedContactController(AuthorizedContactService contacts) {
    this.contacts = contacts;
  }

  @GetMapping("/api/publications/{id}/authorized-contact")
  ResponseEntity<AuthorizedContactService.Contact> read(@PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contacts.read(id));
  }

  @PutMapping("/api/my/publications/{id}/authorized-contact")
  ResponseEntity<Void> authorize(
      @PathVariable long id,
      @AuthenticationPrincipal UserDetails user,
      @RequestBody AuthorizedContactService.Input input) {
    contacts.authorize(id, user.getUsername(), input);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @DeleteMapping("/api/my/publications/{id}/authorized-contact")
  ResponseEntity<Void> withdraw(
      @PathVariable long id,
      @AuthenticationPrincipal UserDetails user,
      @RequestParam(required = false) String reason) {
    contacts.withdraw(id, user.getUsername(), reason);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> known(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                error.getReason() == null ? "Contacto no disponible" : error.getReason()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> malformed() {
    // No refleja el contenido recibido ni lo añade a los logs del conversor JSON.
    return ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Solicitud de contacto inválida"));
  }
}

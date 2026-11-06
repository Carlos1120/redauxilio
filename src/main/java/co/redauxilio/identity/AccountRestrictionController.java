package co.redauxilio.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Consultas y cambios privados de bloqueo, disponibles solo desde la zona de operador. */
@RestController
class AccountRestrictionController {
  private static final Logger LOGGER = LoggerFactory.getLogger(AccountRestrictionController.class);
  private final AccountRestrictionService restrictions;

  AccountRestrictionController(AccountRestrictionService restrictions) {
    this.restrictions = restrictions;
  }

  /** Muestra estado e historial limitado de una cuenta conocida por su correo exacto. */
  @GetMapping("/api/operator/accounts/restriction")
  ResponseEntity<AccountRestrictionService.AccountSnapshot> inspect(@RequestParam String email) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(restrictions.inspect(email));
  }

  /** Permite recorrer auditorías largas sin omitir cambios anteriores al primer bloque. */
  @GetMapping("/api/operator/accounts/restriction/history")
  ResponseEntity<AccountRestrictionService.HistoryPage> olderHistory(
      @RequestParam String email,
      @RequestParam long beforeId,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(restrictions.olderHistory(email, beforeId, limit));
  }

  /** Aplica una acción solicitada explícitamente y devuelve el estado confirmado por SQL. */
  @PostMapping("/api/operator/accounts/restriction")
  ResponseEntity<AccountRestrictionService.AccountSnapshot> change(
      @AuthenticationPrincipal UserDetails operator, @RequestBody RestrictionRequest request) {
    if (request == null)
      throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            restrictions.change(
                request.email(), operator.getUsername(), request.action(), request.reason()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                "No se pudo cambiar el estado de la cuenta. Verifica el correo y el motivo."));
  }

  /** Errores de SQL se correlacionan sin devolver consultas, credenciales ni motivos privados. */
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable(
      DataAccessException error, HttpServletRequest request) {
    String correlation = String.valueOf(request.getAttribute("correlationId"));
    LOGGER.error(
        "Fallo al consultar restricción de cuenta, referencia {} y tipo {}",
        correlation,
        error.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                "No se pudo consultar el estado de la cuenta",
                "reference",
                correlation));
  }

  record RestrictionRequest(String email, String action, String reason) {}
}

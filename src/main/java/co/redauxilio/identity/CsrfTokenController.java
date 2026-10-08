package co.redauxilio.identity;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** Entrega al formulario abierto sin conexión un token CSRF fresco antes de su envío. */
@RestController
class CsrfTokenController {
  @GetMapping("/csrf")
  ResponseEntity<CsrfTokenView> freshToken(@RequestAttribute("_csrf") CsrfToken csrfToken) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfTokenView(csrfToken.getParameterName(), csrfToken.getToken()));
  }

  /** Respuesta privada de caché para actualizar el campo CSRF del formulario actual. */
  record CsrfTokenView(String parameterName, String token) {}
}

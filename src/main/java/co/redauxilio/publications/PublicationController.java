package co.redauxilio.publications;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * Entrada HTTP de la consulta pública. Traduce los parámetros de la petición y delega las reglas en
 * PublicationService; @ResponseBody convierte los resultados a JSON.
 */
@Controller
public class PublicationController {
  private final PublicationService service;
  private final PublicationCreationService creation;

  public PublicationController(PublicationService service, PublicationCreationService creation) {
    this.service = service;
    this.creation = creation;
  }

  /**
   * Consulta reportes combinando los filtros enviados por el navegador. Sin límites geográficos
   * consulta cualquier ubicación; si se envía un límite, exige los cuatro para definir el área. Los
   * filtros de confianza y revisión son independientes; valores o límites inválidos producen HTTP
   * 400, no una lista vacía engañosa.
   */
  @GetMapping("/api/publications")
  @ResponseBody
  public List<PublicationService.Publication> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String operationalStatus,
      @RequestParam(required = false) String confidenceLevel,
      @RequestParam(required = false) String reviewStatus,
      @RequestParam(defaultValue = "false") boolean includeClosed,
      @RequestParam(required = false) Double south,
      @RequestParam(required = false) Double west,
      @RequestParam(required = false) Double north,
      @RequestParam(required = false) Double east) {
    PublicationService.Bounds bounds = null;
    if (south != null || west != null || north != null || east != null) {
      if (south == null || west == null || north == null || east == null)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Área incompleta");
      bounds = new PublicationService.Bounds(south, west, north, east);
    }
    return service.findPublications(
        category, operationalStatus, confidenceLevel, reviewStatus, includeClosed, bounds);
  }

  /** Devuelve el detalle visible; un identificador inexistente u oculto produce HTTP 404. */
  @GetMapping("/api/publications/{id}")
  @ResponseBody
  public PublicationService.Publication detail(@PathVariable long id) {
    return service.findPublication(id);
  }

  /**
   * Publica solo con una cuenta autenticada; devuelve el mismo objeto que lista, mapa y detalle.
   */
  @PostMapping(
      path = "/api/publications",
      consumes = "application/json",
      produces = "application/json")
  @ResponseBody
  public ResponseEntity<PublicationService.Publication> create(
      @AuthenticationPrincipal UserDetails citizen, @RequestBody CreatePublicationRequest request) {
    long id = creation.create(citizen.getUsername(), request);
    return ResponseEntity.created(URI.create("/api/publications/" + id))
        .cacheControl(CacheControl.noStore())
        .body(service.findPublication(id));
  }

  /** Expone solo errores de validación conocidos; nunca una traza o una excepción de base. */
  @ExceptionHandler(ResponseStatusException.class)
  @ResponseBody
  ResponseEntity<Map<String, String>> publicationError(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                exception.getReason() == null ? "Solicitud inválida" : exception.getReason()));
  }
}

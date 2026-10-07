package co.redauxilio.publications;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  public PublicationController(PublicationService service) {
    this.service = service;
  }

  /**
   * Consulta reportes combinando los filtros enviados por el navegador. Sin límites geográficos
   * consulta cualquier ubicación; si se envía un límite, exige los cuatro para definir el área. Los
   * filtros o límites inválidos producen HTTP 400, no una lista vacía engañosa.
   */
  @GetMapping("/api/publications")
  @ResponseBody
  public List<PublicationService.Publication> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String operationalStatus,
      @RequestParam(required = false) String confidenceLevel,
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
        category, operationalStatus, confidenceLevel, includeClosed, bounds);
  }

  /** Devuelve el detalle visible; un identificador inexistente u oculto produce HTTP 404. */
  @GetMapping("/api/publications/{id}")
  @ResponseBody
  public PublicationService.Publication detail(@PathVariable long id) {
    return service.findPublication(id);
  }
}

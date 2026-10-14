package co.redauxilio.geography;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Proxy autenticado de búsqueda; no envía consultas al proveedor desde el navegador. */
@RestController
class PlaceSearchController {
  private final PlaceSearchService service;

  PlaceSearchController(PlaceSearchService service) {
    this.service = service;
  }

  @GetMapping("/api/places")
  ResponseEntity<List<PlaceSearchService.Place>> search(@RequestParam String q) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(q));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> error(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "message",
                exception.getReason() == null ? "Búsqueda no disponible" : exception.getReason()));
  }
}

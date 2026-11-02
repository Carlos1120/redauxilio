package co.redauxilio.publications;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP solo lectura protegido en Spring Security; nunca incorpora información privada al shell
 * público.
 */
@Controller
class ModerationQueueController {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModerationQueueController.class);
  private final ModerationQueueService queue;
  private final ModerationActionService actions;

  ModerationQueueController(ModerationQueueService queue, ModerationActionService actions) {
    this.queue = queue;
    this.actions = actions;
  }

  @GetMapping("/operator")
  String page(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return "moderation";
  }

  @GetMapping("/api/operator/publications")
  @ResponseBody
  ResponseEntity<ModerationQueueService.Page> list(
      @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.list(limit, offset));
  }

  @GetMapping("/api/operator/publications/{id}")
  @ResponseBody
  ResponseEntity<ModerationQueueService.CaseDetail> detail(@PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queue.detail(id));
  }

  /** Paginación privada de denuncias, cambios o decisiones previas del caso seleccionado. */
  @GetMapping("/api/operator/publications/{id}/history/{stream}")
  @ResponseBody
  ResponseEntity<ModerationQueueService.HistoryPage> olderHistory(
      @PathVariable long id,
      @PathVariable String stream,
      @RequestParam long beforeId,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queue.olderHistory(id, stream, beforeId, limit));
  }

  /** Ejecuta una decisión solo con actor obtenido de la sesión y CSRF del formulario. */
  @org.springframework.web.bind.annotation.PostMapping("/api/operator/publications/{id}/actions")
  @ResponseBody
  ResponseEntity<ModerationActionService.DecisionResult> act(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          org.springframework.security.core.userdetails.UserDetails operator,
      @PathVariable long id,
      @org.springframework.web.bind.annotation.RequestBody
          ModerationActionService.ActionRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(actions.act(id, operator.getUsername(), request));
  }

  @GetMapping("/api/operator/permissions/history")
  @ResponseBody
  ResponseEntity<List<ModerationQueueService.PermissionChange>> permissions(
      @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queue.permissions(limit, offset));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> invalid(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Solicitud de moderación no disponible o inválida"));
  }

  /**
   * Falla cerrada sin SQL ni razones privadas; el identificador vincula mensaje y registro técnico.
   */
  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Map<String, String>> unavailable(
      DataAccessException error, HttpServletRequest request) {
    String correlation = String.valueOf(request.getAttribute("correlationId"));
    LOGGER.error(
        "Fallo de lectura operativa, referencia {} y tipo {}",
        correlation,
        error.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "No se pudo consultar moderación", "reference", correlation));
  }
}

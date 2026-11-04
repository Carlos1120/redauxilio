package co.redauxilio.publications;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos de respaldo y operaciones privadas de permiso oficial. */
@RestController
class OfficialVerificationController {
  private final OfficialVerificationService verifications;

  OfficialVerificationController(OfficialVerificationService verifications) {
    this.verifications = verifications;
  }

  /** Publica solo verificaciones vigentes de la versión visible. */
  @GetMapping("/api/publications/{publicationId}/official-verifications")
  ResponseEntity<List<OfficialVerificationService.PublicVerification>> listPublic(
      @PathVariable long publicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(verifications.listPublic(publicationId));
  }

  /** Comunica a la interfaz ciudadana si la sesión tiene permiso oficial vigente. */
  @GetMapping("/api/my/verification-permission")
  ResponseEntity<Map<String, Boolean>> ownPermission(@AuthenticationPrincipal UserDetails citizen) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("canVerify", verifications.canVerify(citizen.getUsername())));
  }

  /** Registra nota o referencia oficial, sujeto a permiso persistido y CSRF. */
  @PostMapping("/api/my/publications/{publicationId}/official-verifications")
  ResponseEntity<Map<String, Long>> verify(
      @PathVariable long publicationId,
      @AuthenticationPrincipal UserDetails citizen,
      @RequestBody VerificationRequest request) {
    long id =
        verifications.verify(
            publicationId, citizen.getUsername(), request.note(), request.reference());
    return ResponseEntity.created(
            URI.create("/api/publications/" + publicationId + "/official-verifications/" + id))
        .cacheControl(CacheControl.noStore())
        .body(Map.of("id", id));
  }

  /** Corrección motivada desde el espacio del operador, además de entidades autorizadas. */
  @PostMapping("/api/operator/publications/{publicationId}/official-verifications")
  ResponseEntity<Map<String, Long>> verifyAsOperator(
      @PathVariable long publicationId,
      @AuthenticationPrincipal UserDetails operator,
      @RequestBody VerificationRequest request) {
    long id =
        verifications.verifyAsOperator(
            publicationId,
            operator.getUsername(),
            request.note(),
            request.reference(),
            request.reason());
    return ResponseEntity.created(
            URI.create("/api/publications/" + publicationId + "/official-verifications/" + id))
        .cacheControl(CacheControl.noStore())
        .body(Map.of("id", id));
  }

  /** La auditoría operativa incluye verificaciones retiradas y versiones históricas. */
  @GetMapping("/api/operator/publications/{publicationId}/official-verifications")
  ResponseEntity<List<OfficialVerificationService.VerificationHistory>> history(
      @PathVariable long publicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(verifications.history(publicationId));
  }

  /** Concede o retira permiso a una cuenta existente; esta ruta es exclusiva del operador. */
  @PostMapping("/api/operator/verifier-permissions")
  ResponseEntity<Map<String, Boolean>> changePermission(
      @AuthenticationPrincipal UserDetails operator, @RequestBody PermissionRequest request) {
    boolean granted =
        verifications.changePermission(
            request.email(), operator.getUsername(), request.action(), request.reason());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("granted", granted));
  }

  /** Consulta interna de verificadores autorizados para facilitar el retiro del permiso. */
  @GetMapping("/api/operator/verifier-permissions")
  ResponseEntity<List<OfficialVerificationService.Verifier>> activeVerifiers() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(verifications.activeVerifiers());
  }

  /** El historial privado conserva actor, motivo y efecto de cada cambio de permiso. */
  @GetMapping("/api/operator/verifier-permissions/history")
  ResponseEntity<List<OfficialVerificationService.PermissionAudit>> permissionHistory(
      @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(verifications.permissionHistory(limit, offset));
  }

  /** Un operador retira verificaciones por corrección sin borrar el evento original. */
  @PostMapping(
      "/api/operator/publications/{publicationId}/official-verifications/{verificationId}/revoke")
  ResponseEntity<Void> revoke(
      @PathVariable long publicationId,
      @PathVariable long verificationId,
      @AuthenticationPrincipal UserDetails operator,
      @RequestBody RevokeRequest request) {
    verifications.revoke(publicationId, verificationId, operator.getUsername(), request.reason());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  record VerificationRequest(String note, String reference, String reason) {}

  record PermissionRequest(String email, String action, String reason) {}

  record RevokeRequest(String reason) {}
}

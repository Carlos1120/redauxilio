package co.redauxilio.identity;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Entrega una clave AES estable solo al titular de una sesión autenticada y sin caché. */
@RestController
class DraftKeyController {
  private final JdbcTemplate jdbc;
  private final SecureRandom random = new SecureRandom();

  DraftKeyController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping(path = "/api/my/draft-key", produces = "application/json")
  ResponseEntity<Map<String, String>> key(@AuthenticationPrincipal UserDetails citizen) {
    if (citizen == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    String email = citizen.getUsername().trim().toLowerCase(Locale.ROOT);
    List<Long> ids =
        jdbc.query(
            "SELECT id FROM citizen_account WHERE email = ?", (rs, row) -> rs.getLong(1), email);
    if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    long id = ids.get(0);
    List<String> keys = existingKeys(id);
    if (keys.isEmpty()) {
      byte[] secret = new byte[32];
      random.nextBytes(secret);
      String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
      try {
        jdbc.update(
            "INSERT INTO citizen_draft_key(account_id,key_base64) VALUES (?,?)", id, generated);
      } catch (DuplicateKeyException concurrentCreation) {
        // Una petición concurrente pudo crearla antes: si no existe, propaga el error.
        if (existingKeys(id).isEmpty()) throw concurrentCreation;
      }
      keys = existingKeys(id);
    }
    if (keys.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("owner", String.valueOf(id), "key", keys.get(0)));
  }

  private List<String> existingKeys(long id) {
    return jdbc.query(
        "SELECT key_base64 FROM citizen_draft_key WHERE account_id = ?",
        (rs, row) -> rs.getString(1),
        id);
  }
}

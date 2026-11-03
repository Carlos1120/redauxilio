package co.redauxilio.publications;

import co.redauxilio.identity.OperatorPermissions;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contacto adulto autorizado separado del contenido cacheable. Nunca escribe el valor en snapshots,
 * motivos, logs o respuestas de escritura. Autorización/retiro y auditoría comparten transacción.
 */
@Service
class AuthorizedContactService {
  private final JdbcTemplate jdbc;
  private final OperatorPermissions operators;
  private final Clock clock;

  AuthorizedContactService(JdbcTemplate jdbc, OperatorPermissions operators, Clock clock) {
    this.jdbc = jdbc;
    this.operators = operators;
    this.clock = clock;
  }

  /** Lectura pública bajo demanda de una autorización vigente; el controlador impide almacenar. */
  @Transactional(readOnly = true)
  Contact read(long id) {
    return jdbc
        .query(
            "SELECT c.contact_value FROM publication_authorized_contact c JOIN citizen_publication p ON p.id=c.publication_id WHERE p.id=? AND p.category='MISSING_PERSON' AND p.visible=TRUE",
            (row, index) -> new Contact(row.getString(1)),
            id)
        .stream()
        .findFirst()
        .orElseThrow(AuthorizedContactService::unavailable);
  }

  /** Solo el autor autoriza un contacto; consentimiento y declaración de adulto son afirmativos. */
  @Transactional
  void authorize(long id, String email, Input input) {
    Access access = lock(id, email);
    if (access.actor != access.author || !access.visible) throw unavailable();
    if (access.closed)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Reabre el caso antes de autorizar un contacto");
    if (input == null
        || !Boolean.TRUE.equals(input.consent())
        || !Boolean.TRUE.equals(input.adultContact()))
      throw invalid(
          "Confirma autorización expresa y que el contacto pertenece a una persona adulta");
    String value = input.value() == null ? "" : input.value().trim();
    if (value.length() > 160
        || !(value.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
            || (value.matches("[+0-9 ()-]{7,30}")
                && value.replaceAll("[^0-9]", "").length() >= 7
                && value.replaceAll("[^0-9]", "").length() <= 15)))
      throw invalid("Indica un correo o teléfono adulto válido, hasta 160 caracteres");
    String previous = current(id);
    if (Objects.equals(value, previous)) return;
    jdbc.update("DELETE FROM publication_authorized_contact WHERE publication_id=?", id);
    jdbc.update(
        "INSERT INTO publication_authorized_contact(publication_id,contact_value,authorized_at) VALUES (?,?,?)",
        id,
        value,
        Timestamp.from(clock.instant()));
    audit(
        id,
        access.actor,
        previous != null,
        true,
        "CONTACT",
        "Autorización expresa de contacto adulto");
  }

  /**
   * Autor retira incluso oculto/cerrado; operador requiere permiso persistente y motivo acotado.
   */
  @Transactional
  void withdraw(long id, String email, String reason) {
    Access access = lock(id, email);
    boolean author = access.actor == access.author;
    if (!author && !operators.isOperator(email)) throw unavailable();
    if (!author
        && !Set.of("PROTECCION_DATOS", "SOLICITUD_AUTOR", "CONTACTO_INCORRECTO")
            .contains(reason == null ? "" : reason))
      throw invalid("Selecciona el motivo de retiro del contacto");
    if (current(id) == null) return;
    jdbc.update("DELETE FROM publication_authorized_contact WHERE publication_id=?", id);
    audit(
        id,
        access.actor,
        true,
        false,
        "CONTACT_WITHDRAW",
        author ? "Retiro solicitado por el autor" : reason);
  }

  private String current(long id) {
    return jdbc
        .query(
            "SELECT contact_value FROM publication_authorized_contact WHERE publication_id=?",
            (row, index) -> row.getString(1),
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Access lock(long id, String email) {
    if (id < 1_000_000) throw unavailable();
    Long actor =
        jdbc
            .query(
                "SELECT id FROM citizen_account WHERE email=?",
                (row, index) -> row.getLong(1),
                email)
            .stream()
            .findFirst()
            .orElseThrow(AuthorizedContactService::unavailable);
    return jdbc
        .query(
            "SELECT author_id,visible,closed FROM citizen_publication WHERE id=? AND category='MISSING_PERSON' FOR UPDATE",
            (row, index) -> new Access(actor, row.getLong(1), row.getBoolean(2), row.getBoolean(3)),
            id)
        .stream()
        .findFirst()
        .orElseThrow(AuthorizedContactService::unavailable);
  }

  private void audit(
      long id, long actor, boolean before, boolean after, String action, String reason) {
    // Solo existencia de consentimiento: no almacena contacto anterior, ni siquiera como motivo.
    jdbc.update(
        "INSERT INTO citizen_publication_change(publication_id,actor_id,action,before_snapshot,after_snapshot,reason,changed_at) VALUES (?,?,?,?,?,?,?)",
        id,
        actor,
        action,
        "{\"contactAuthorized\":" + before + "}",
        "{\"contactAuthorized\":" + after + "}",
        reason,
        Timestamp.from(clock.instant()));
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contacto no disponible");
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private record Access(long actor, long author, boolean visible, boolean closed) {}

  record Input(String value, Boolean consent, Boolean adultContact) {
    @Override
    public String toString() {
      return "ContactInput[redacted]";
    }
  }

  record Contact(String value) {
    @Override
    public String toString() {
      return "AuthorizedContact[redacted]";
    }
  }
}

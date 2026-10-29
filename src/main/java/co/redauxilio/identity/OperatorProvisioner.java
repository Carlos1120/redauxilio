package co.redauxilio.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ejecuta una solicitud administrativa explícita al iniciar; deshabilitado si no se indica acción.
 */
@Component
class OperatorProvisioner implements ApplicationRunner {
  private final OperatorPermissions permissions;
  private final String action, email, actor, reason, requestId;
  private final long accountId;

  OperatorProvisioner(
      OperatorPermissions permissions,
      @Value("${redauxilio.operator.provision.action:}") String action,
      @Value("${redauxilio.operator.provision.account-id:0}") long accountId,
      @Value("${redauxilio.operator.provision.expected-email:}") String email,
      @Value("${redauxilio.operator.provision.actor:}") String actor,
      @Value("${redauxilio.operator.provision.reason:}") String reason,
      @Value("${redauxilio.operator.provision.request-id:}") String requestId) {
    this.permissions = permissions;
    this.action = action;
    this.accountId = accountId;
    this.email = email;
    this.actor = actor;
    this.reason = reason;
    this.requestId = requestId;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (!action.isBlank())
      permissions.provision(accountId, email, action, actor, reason, requestId);
  }
}

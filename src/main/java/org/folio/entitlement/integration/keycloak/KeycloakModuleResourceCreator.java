package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@Log4j2
@RequiredArgsConstructor
@KeycloakCallsRetryable
public class KeycloakModuleResourceCreator extends ModuleDatabaseLoggingStage {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ModuleStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    threadLocalModuleStageContext.set(context);

    var realm = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    keycloakService.updateAuthResources(null, context.getModuleDescriptor(), realm);
  }

  @Override
  public void cancel(ModuleStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    var request = context.getEntitlementRequest();
    var moduleDescriptor = context.getModuleDescriptor();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Keycloak resources during rollback: moduleId = {}",  context.getModuleId());
      return;
    }

    var tenantName = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    keycloakService.removeAuthResources(moduleDescriptor, tenantName);

    threadLocalModuleStageContext.clear();
  }

  @Override
  public boolean shouldCancelIfFailed(ModuleStageContext context) {
    return true;
  }
}

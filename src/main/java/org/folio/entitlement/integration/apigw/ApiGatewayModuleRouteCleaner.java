package org.folio.entitlement.integration.apigw;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.gateway.ApiGatewayService;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class ApiGatewayModuleRouteCleaner extends ModuleDatabaseLoggingStage {

  private final ApiGatewayService apiGatewayService;
  private final ApiGatewayConfigurationProperties properties;
  private final EntitlementModuleService entitlementModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }
    var moduleId = context.getModuleId();
    var entitlementExists = entitlementModuleService.isEntitlementExist(moduleId);
    if (!entitlementExists && properties.getRouteManagement().isEnabled()) {
      deleteServiceAndRoutes(moduleId);
      return;
    }
    if (properties.getTenantChecks().isEnabled()) {
      apiGatewayService.removeTenantFromModuleRoutes(moduleId, context.getTenantName());
    }
  }

  private void deleteServiceAndRoutes(String moduleId) {
    try {
      apiGatewayService.deleteServiceRoutes(moduleId);
    } catch (NoSuchElementException e) {
      log.debug("API gateway service already absent, skipping cleanup: moduleId = {}", moduleId);
      return;
    }
    apiGatewayService.deleteService(moduleId);
    log.debug("Deleted API gateway service and routes for last-entitled module: moduleId = {}", moduleId);
  }
}

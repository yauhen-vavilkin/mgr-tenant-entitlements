package org.folio.entitlement.integration.apigw;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.gateway.ApiGatewayService;
import org.folio.common.gateway.model.GatewayServiceDefinition;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class ApiGatewayModuleRouteCreator extends ModuleDatabaseLoggingStage {

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
    var location = context.getModuleDiscovery();
    apiGatewayService.upsertService(new GatewayServiceDefinition().name(moduleId).url(location));
    log.debug("Upserted API gateway service: moduleId = {}", moduleId);
    if (properties.getRouteManagement().isEnabled() && !entitlementModuleService.isEntitlementExist(moduleId)) {
      apiGatewayService.addRoutes(List.of(context.getModuleDescriptor()));
      log.debug("Added API gateway routes for module: moduleId = {}", moduleId);
    }
    if (properties.getTenantChecks().isEnabled()) {
      apiGatewayService.addTenantToModuleRoutes(moduleId, context.getTenantName());
      log.debug("Added tenant to API gateway routes: moduleId = {}, tenant = {}", moduleId, context.getTenantName());
    }
  }

  @Override
  public void cancel(ModuleStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }
    var request = context.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of API gateway routes during rollback: moduleId = {}", context.getModuleId());
      return;
    }
    var moduleId = context.getModuleId();
    if (properties.getRouteManagement().isEnabled() && !entitlementModuleService.isEntitlementExist(moduleId)) {
      deleteServiceAndRoutes(moduleId);
      return;
    }
    if (properties.getTenantChecks().isEnabled()) {
      removeTenantFromModuleRoutesQuietly(moduleId, context.getTenantName());
    }
  }

  @Override
  public boolean shouldCancelIfFailed(ModuleStageContext context) {
    return true;
  }

  private void deleteServiceAndRoutes(String moduleId) {
    deleteServiceRoutesQuietly(moduleId);
    deleteServiceQuietly(moduleId);
    log.debug("Deleted API gateway service and routes on cancel: moduleId = {}", moduleId);
  }

  private void deleteServiceRoutesQuietly(String moduleId) {
    try {
      apiGatewayService.deleteServiceRoutes(moduleId);
    } catch (Exception e) {
      log.error("Failed to delete API gateway service routes, skipping: moduleId = {}, error = {}", moduleId,
        e.getMessage());
    }
  }

  private void deleteServiceQuietly(String moduleId) {
    try {
      apiGatewayService.deleteService(moduleId);
    } catch (Exception e) {
      log.error("Failed to delete API gateway service, skipping: moduleId = {}", moduleId);
    }
  }

  private void removeTenantFromModuleRoutesQuietly(String moduleId, String tenantName) {
    try {
      apiGatewayService.removeTenantFromModuleRoutes(moduleId, tenantName);
    } catch (Exception e) {
      log.error("Failed to remove tenant from API gateway routes, skipping: moduleId = {}, tenant = {}", moduleId,
        tenantName);
    }
  }
}

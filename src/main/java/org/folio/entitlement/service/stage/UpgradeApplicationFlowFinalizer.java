package org.folio.entitlement.service.stage;

import java.util.UUID;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class UpgradeApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext>  {

  private final EntitlementCrudService entitlementCrudService;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param entitlementCrudService - {@link EntitlementCrudService} bean
   */
  public UpgradeApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
    @Qualifier("applicationFlowFinalizerStatusProvider")
    FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider,
    EntitlementCrudService entitlementCrudService) {
    super(applicationFlowRepository, statusProvider);
    this.entitlementCrudService = entitlementCrudService;
  }

  @Override
  protected void afterFlowStatusUpdate(ApplicationStageContext context) {
    if (context.getFenceToken() != null && !context.isFenceWriteSucceeded()) {
      return;
    }
    var tenantId = context.getTenantId();
    entitlementCrudService.delete(buildEntitlement(tenantId, context.getEntitledApplicationId()));
    entitlementCrudService.save(buildEntitlement(tenantId, context.getApplicationId()));
  }

  private static Entitlement buildEntitlement(UUID tenantId, String applicationId) {
    return new Entitlement().applicationId(applicationId).tenantId(tenantId);
  }
}

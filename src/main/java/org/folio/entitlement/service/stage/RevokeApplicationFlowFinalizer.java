package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RevokeApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  public RevokeApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
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
    var applicationId = context.getApplicationId();
    var entitlement = new Entitlement().applicationId(applicationId).tenantId(context.getTenantId());
    entitlementCrudService.delete(entitlement);
  }
}

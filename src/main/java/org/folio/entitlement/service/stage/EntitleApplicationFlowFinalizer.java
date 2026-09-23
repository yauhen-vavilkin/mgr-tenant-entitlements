package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EntitleApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   * @param entitlementCrudService - {@link EntitlementCrudService} bean
   */
  public EntitleApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository,
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
    var entitlement = buildEntitlementFromContext(context);
    entitlementCrudService.save(entitlement);
  }

  @Override
  @Transactional
  public void cancel(ApplicationStageContext context) {
    if (context.getFenceToken() != null && !guardFenceToken(context)) {
      return;
    }
    var entitlement = buildEntitlementFromContext(context);
    entitlementCrudService.delete(entitlement);
  }

  private static Entitlement buildEntitlementFromContext(ApplicationStageContext context) {
    return new Entitlement().applicationId(context.getApplicationId()).tenantId(context.getTenantId());
  }
}

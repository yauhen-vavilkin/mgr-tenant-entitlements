package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDependencyCleaner extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationDependencyService applicationDependencyService;

  @Override
  public void execute(ApplicationStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    applicationDependencyService.deleteEntitlementDependencies(
      context.getTenantId(),
      context.getApplicationId(),
      context.getApplicationDescriptor().getDependencies());
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    applicationDependencyService.saveEntitlementDependencies(
      context.getTenantId(),
      context.getApplicationId(),
      context.getApplicationDescriptor().getDependencies());
  }
}

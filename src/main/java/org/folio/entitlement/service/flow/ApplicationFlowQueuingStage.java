package org.folio.entitlement.service.flow;

import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicationFlowQueuingStage extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;

  @Override
  @Transactional
  public void execute(CommonStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    var request = context.getEntitlementRequest();
    var flows = applicationFlowService.createQueuedApplicationFlows(context.getCurrentFlowId(), request);
    var applicationFlowsMap = toHashMap(flows, ApplicationFlow::getApplicationId, ApplicationFlow::getId);
    context.withQueuedApplicationFlows(applicationFlowsMap);
  }
}

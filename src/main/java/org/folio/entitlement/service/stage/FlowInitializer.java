package org.folio.entitlement.service.stage;

import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.configuration.InstanceIdContext;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.flow.FlowService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlowInitializer extends DatabaseLoggingStage<CommonStageContext> {

  private final FlowService flowService;
  private final InstanceIdContext instanceIdContext;

  @Override
  public void execute(CommonStageContext context) {
    var entitlementRequest = context.getEntitlementRequest();
    var flow = new Flow()
      .id(context.getCurrentFlowId())
      .tenantId(entitlementRequest.getTenantId())
      .ownerInstanceId(instanceIdContext.getInstanceId())
      .status(ExecutionStatus.IN_PROGRESS)
      .type(entitlementRequest.getType())
      .startedAt(Date.from(Instant.now()));

    flowService.create(flow);
  }
}

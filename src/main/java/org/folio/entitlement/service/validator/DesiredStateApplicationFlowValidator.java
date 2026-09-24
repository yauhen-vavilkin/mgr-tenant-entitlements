package org.folio.entitlement.service.validator;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.service.validator.ValidatorUtils.validateApplicationFlow;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionBucket;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowRecoveryService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateApplicationFlowValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;
  private final FlowRecoveryService flowRecoveryService;

  @Override
  public void execute(CommonStageContext context) {
    validate(context.getEntitlementRequest(), context.getApplicationStateTransitionPlan());
  }

  public void validate(EntitlementRequest request, ApplicationStateTransitionPlan transitionPlan) {

    var validationErrors = transitionPlan.nonEmptyBuckets()
      .flatMap(transitionBucket -> validateApplicationFlowsInBucket(transitionBucket, request.getTenantId()))
      .toList();

    if (isNotEmpty(validationErrors)) {
      throw new RequestValidationException("Found validation errors in desired state request", validationErrors);
    }
  }

  private Stream<Parameter> validateApplicationFlowsInBucket(ApplicationStateTransitionBucket transitionBucket,
    UUID tenantId) {
    var type = transitionBucket.getEntitlementType();
    var applicationIds = transitionBucket.getApplicationIds();

    var applicationFlows = findApplicationFlows(type, applicationIds, tenantId);
    if (flowRecoveryService.recover(applicationFlows)) {
      applicationFlows = findApplicationFlows(type, applicationIds, tenantId);
    }

    return applicationFlows.stream()
      .map(applicationFlow -> validateApplicationFlow(applicationFlow, type))
      .flatMap(Optional::stream);
  }

  private List<ApplicationFlow> findApplicationFlows(EntitlementType type, Set<String> applicationIds,
    UUID tenantId) {
    return type == REVOKE
      ? applicationFlowService.findLastFlows(applicationIds, tenantId)
      : applicationFlowService.findLastFlowsByNames(getNames(applicationIds), tenantId);
  }
}

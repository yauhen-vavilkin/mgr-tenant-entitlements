package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.INTERRUPTED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.InstanceHeartbeatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reclaims blocking application flows whose owning MTE instance is no longer alive. */
@Log4j2
@Service
@RequiredArgsConstructor
public class FlowRecoveryService {

  private final FlowRepository flowRepository;
  private final ApplicationFlowRepository applicationFlowRepository;
  private final InstanceHeartbeatService heartbeatService;

  /** Recovers each distinct stale parent flow represented by the validation result. */
  @Transactional
  public boolean recover(List<ApplicationFlow> applicationFlows) {
    try {
      var recoveredFlowIds = new HashSet<UUID>();
      var recoveryAttempted = false;
      for (var applicationFlow : applicationFlows) {
        if (isBlocking(applicationFlow) && applicationFlow.getFlowId() != null
            && recoveredFlowIds.add(applicationFlow.getFlowId())) {
          recoveryAttempted |= recoverFlow(applicationFlow.getFlowId());
        }
      }
      return recoveryAttempted;
    } catch (RuntimeException e) {
      log.error("Failed to recover orphaned entitlement flow", e);
      throw new IllegalStateException("Failed to recover orphaned entitlement flow", e);
    }
  }

  private boolean recoverFlow(UUID flowId) {
    var flow = flowRepository.findById(flowId);
    if (flow.isEmpty()) {
      log.warn("Unable to recover application flow because parent flow was not found [flowId: {}]", flowId);
      return false;
    }

    var parent = flow.get();
    var owner = parent.getOwnerInstanceId();
    if (NON_TERMINAL_STATUSES.contains(parent.getStatus()) && owner != null && heartbeatService.isAlive(owner)) {
      log.info("Flow recovery skipped because owner is alive [flowId: {}, ownerInstanceId: {}]", flowId, owner);
      return false;
    }

    return interruptFlow(flowId, owner);
  }

  private boolean interruptFlow(UUID flowId, UUID owner) {
    var finishedAt = ZonedDateTime.now(ZoneId.systemDefault());
    var flowUpdated = flowRepository.updateStatusIfCurrentIn(flowId, INTERRUPTED, NON_TERMINAL_STATUSES, finishedAt);
    var applicationFlows = applicationFlowRepository.updateStatusByFlowIdIfCurrentIn(
      flowId, INTERRUPTED, NON_TERMINAL_STATUSES, finishedAt);
    if (flowUpdated == 0 && applicationFlows == 0) {
      log.info("Flow recovery found no blocking rows to interrupt [flowId: {}, previousOwnerInstanceId: {}]",
        flowId, owner);
    } else {
      log.warn("Orphaned flow recovered [flowId: {}, previousOwnerInstanceId: {}, rootUpdated: {}, "
          + "applicationFlows: {}, outcome: {}]",
        flowId, owner, flowUpdated, applicationFlows, INTERRUPTED);
    }
    return true;
  }

  private boolean isBlocking(ApplicationFlow applicationFlow) {
    return applicationFlow.getStatus() != null
      && NON_TERMINAL_STATUSES.contains(EntityExecutionStatus.from(applicationFlow.getStatus()));
  }
}

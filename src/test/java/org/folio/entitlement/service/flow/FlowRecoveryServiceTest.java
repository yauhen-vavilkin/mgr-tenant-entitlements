package org.folio.entitlement.service.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.INTERRUPTED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.QUEUED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.InstanceHeartbeatService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowRecoveryServiceTest {

  private static final UUID FLOW_ID = UUID.randomUUID();
  private static final UUID SECOND_FLOW_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();

  @Mock private FlowRepository flowRepository;
  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Mock private InstanceHeartbeatService heartbeatService;

  @Test
  void recover_processesEveryDistinctParent() {
    var service = service();
    var firstParent = parent(QUEUED, OWNER_ID);
    var secondParent = parent(IN_PROGRESS, OWNER_ID);
    when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(firstParent));
    when(flowRepository.findById(SECOND_FLOW_ID)).thenReturn(Optional.of(secondParent));
    when(heartbeatService.isAlive(OWNER_ID)).thenReturn(false);
    when(flowRepository.updateStatusIfCurrentIn(any(), eq(INTERRUPTED), any(), any())).thenReturn(1);
    when(applicationFlowRepository.updateStatusByFlowIdIfCurrentIn(
      any(), eq(INTERRUPTED), any(), any())).thenReturn(1);

    var result = service.recover(List.of(applicationFlow(QUEUED, FLOW_ID), applicationFlow(IN_PROGRESS, FLOW_ID),
      applicationFlow(IN_PROGRESS, SECOND_FLOW_ID)));

    assertThat(result).isTrue();
    verify(flowRepository).findById(FLOW_ID);
    verify(flowRepository).findById(SECOND_FLOW_ID);
    verify(flowRepository).updateStatusIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
    verify(flowRepository).updateStatusIfCurrentIn(eq(SECOND_FLOW_ID), eq(INTERRUPTED), any(), any());
    verify(applicationFlowRepository).updateStatusByFlowIdIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
    verify(applicationFlowRepository).updateStatusByFlowIdIfCurrentIn(
      eq(SECOND_FLOW_ID), eq(INTERRUPTED), any(), any());
  }

  @Test
  void recover_doesNotInterruptFlowWithLiveOwner() {
    var service = service();
    when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(parent(QUEUED, OWNER_ID)));
    when(heartbeatService.isAlive(OWNER_ID)).thenReturn(true);

    var result = service.recover(List.of(applicationFlow(QUEUED, FLOW_ID)));

    assertThat(result).isFalse();
    verify(flowRepository, never()).updateStatusIfCurrentIn(any(), any(), any(), any());
    verify(applicationFlowRepository, never()).updateStatusByFlowIdIfCurrentIn(any(), any(), any(), any());
  }

  @Test
  void recover_interruptsOrphanedChildWhenParentIsMissing() {
    var service = service();
    when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.empty());
    when(applicationFlowRepository.updateStatusByFlowIdIfCurrentIn(
      eq(FLOW_ID), eq(INTERRUPTED), any(), any())).thenReturn(1);

    var result = service.recover(List.of(applicationFlow(QUEUED, FLOW_ID)));

    assertThat(result).isTrue();
    verify(applicationFlowRepository).updateStatusByFlowIdIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
    verify(flowRepository, never()).updateStatusIfCurrentIn(any(), any(), any(), any());
  }

  @Test
  void recover_interruptsOrphanedChildWhenParentIsTerminal() {
    var service = service();
    when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(parent(FAILED, OWNER_ID)));
    when(flowRepository.updateStatusIfCurrentIn(any(), eq(INTERRUPTED), any(), any())).thenReturn(0);
    when(applicationFlowRepository.updateStatusByFlowIdIfCurrentIn(any(), eq(INTERRUPTED), any(), any()))
      .thenReturn(1);

    var result = service.recover(List.of(applicationFlow(QUEUED, FLOW_ID)));

    assertThat(result).isTrue();
    verify(heartbeatService, never()).isAlive(any());
    verify(applicationFlowRepository).updateStatusByFlowIdIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
  }

  @Test
  void recover_returnsTrueWhenConcurrentRecoveryUpdatesLoseRace() {
    var service = service();
    when(flowRepository.findById(FLOW_ID)).thenReturn(Optional.of(parent(QUEUED, OWNER_ID)));
    when(heartbeatService.isAlive(OWNER_ID)).thenReturn(false);
    when(flowRepository.updateStatusIfCurrentIn(any(), eq(INTERRUPTED), any(), any())).thenReturn(0);
    when(applicationFlowRepository.updateStatusByFlowIdIfCurrentIn(any(), eq(INTERRUPTED), any(), any()))
      .thenReturn(0);

    var result = service.recover(List.of(applicationFlow(QUEUED, FLOW_ID)));

    assertThat(result).isTrue();
    verify(flowRepository).updateStatusIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
    verify(applicationFlowRepository).updateStatusByFlowIdIfCurrentIn(eq(FLOW_ID), eq(INTERRUPTED), any(), any());
  }

  private FlowRecoveryService service() {
    return new FlowRecoveryService(flowRepository, applicationFlowRepository, heartbeatService);
  }

  private static FlowEntity parent(org.folio.entitlement.domain.entity.type.EntityExecutionStatus status,
    UUID ownerId) {
    var parent = new FlowEntity();
    parent.setId(FLOW_ID);
    parent.setStatus(status);
    parent.setOwnerInstanceId(ownerId);
    return parent;
  }

  private static ApplicationFlow applicationFlow(
    org.folio.entitlement.domain.entity.type.EntityExecutionStatus status, UUID flowId) {
    return new ApplicationFlow().flowId(flowId).status(ExecutionStatus.valueOf(status.name()));
  }
}

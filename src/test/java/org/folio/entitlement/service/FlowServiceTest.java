package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.applicationFlow;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.flow;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.flowEntity;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.entity.type.EntityFlowEntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.FlowMapper;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

  @InjectMocks private FlowService flowService;

  @Mock private FlowMapper flowMapper;
  @Mock private FlowRepository flowRepository;
  @Mock private FlowStageService flowStageService;
  @Mock private ApplicationFlowService applicationFlowService;
  @Mock private InstanceContext instanceContext;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Nested
  @DisplayName("find")
  class Find {

    @Test
    void positive() {
      var query = "tenantId = " + TENANT_ID;
      var flow = flow();
      var flowEntity = flowEntity();
      var offsetRequest = OffsetRequest.of(0, 100);
      var applicationFlow = applicationFlow();
      var applicationFlowsMap = Map.of(APPLICATION_FLOW_ID, List.of(applicationFlow));

      when(flowRepository.findByCql(query, offsetRequest)).thenReturn(new PageImpl<>(List.of(flowEntity)));
      when(applicationFlowService.findByFlowIds(List.of(FLOW_ID))).thenReturn(applicationFlowsMap);
      when(flowMapper.map(flowEntity)).thenReturn(flow);

      var result = flowService.find(query, 100, 0);
      assertThat(result).isEqualTo(SearchResult.of(List.of(flow)));
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void negative_emptyResult() {
      when(flowRepository.getReferenceById(FLOW_ID)).thenThrow(EntityNotFoundException.class);
      assertThatThrownBy(() -> flowService.getById(FLOW_ID, false))
        .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void positive_includeStagesIsFalse() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var flow = flow(FINISHED, Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));
      var flowEntity = flowEntity(startedAt, finishedAt);
      var applicationFlows = List.of(applicationFlow());

      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);
      when(applicationFlowService.findByFlowId(FLOW_ID, false)).thenReturn(applicationFlows);

      var result = flowService.getById(FLOW_ID, false);

      assertThat(result).isEqualTo(new Flow()
        .id(FLOW_ID)
        .applicationFlows(applicationFlows)
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .status(FINISHED)
        .type(ENTITLE));
    }

    @Test
    void positive_includeStagesIsTrue() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var flow = flow(FINISHED, Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));
      var flowEntity = flowEntity(startedAt, finishedAt);
      var flowStages = List.of(stage(FLOW_ID, "flow-stage"));
      var applicationFlows = List.of(applicationFlow().addStagesItem(stage(APPLICATION_FLOW_ID, "app-flow-stage")));

      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);
      when(applicationFlowService.findByFlowId(FLOW_ID, true)).thenReturn(applicationFlows);
      when(flowStageService.findByFlowId(FLOW_ID)).thenReturn(SearchResult.of(flowStages));

      var result = flowService.getById(FLOW_ID, true);

      assertThat(result).isEqualTo(new Flow()
        .id(FLOW_ID)
        .applicationFlows(applicationFlows)
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .stages(flowStages)
        .status(FINISHED)
        .type(ENTITLE));
    }

    private FlowStage stage(UUID flowId, String stageName) {
      return new FlowStage()
        .flowId(flowId)
        .name(stageName)
        .status(FINISHED);
    }
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    void positive() {
      var flow = flow();
      var flowEntity = flowEntity();

      when(flowRepository.findStatusById(FLOW_ID)).thenReturn(Optional.empty());
      when(flowMapper.map(flow)).thenReturn(flowEntity);
      when(flowRepository.save(flowEntity)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);

      var result = flowService.create(flow);

      assertThat(result).isEqualTo(flow);
    }

    @Test
    void negative_flowAlreadyExists() {
      var flow = flow();

      when(flowRepository.findStatusById(FLOW_ID)).thenReturn(Optional.of(EntityExecutionStatus.FAILED));

      assertThatThrownBy(() -> flowService.create(flow))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Flow cannot be started, because it has already been created "
          + "with FAILED status [flowId: %s]", FLOW_ID);

      verify(flowRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("createFailed")
  class CreateFailed {

    @Test
    void positive() {
      var flowEntity = flowEntity();
      var instanceId = UUID.randomUUID();
      final var request = EntitlementRequest.builder()
        .applications(List.of(APPLICATION_ID))
        .tenantId(TENANT_ID)
        .type(ENTITLE)
        .build();
      var flowCaptor = ArgumentCaptor.forClass(Flow.class);

      when(instanceContext.getInstanceId()).thenReturn(instanceId);
      when(flowMapper.map(flowCaptor.capture())).thenReturn(flowEntity);
      when(flowRepository.saveAndFlush(flowEntity)).thenReturn(flowEntity);

      flowService.createFailed(FLOW_ID, request);

      var mappedFlow = flowCaptor.getValue();
      assertThat(mappedFlow.getId()).isEqualTo(FLOW_ID);
      assertThat(mappedFlow.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(mappedFlow.getStatus()).isEqualTo(ExecutionStatus.FAILED);
      assertThat(mappedFlow.getType()).isEqualTo(ENTITLE);
      assertThat(mappedFlow.getOwnerInstanceId()).isEqualTo(instanceId);
      assertThat(mappedFlow.getStartedAt()).isNull();
      assertThat(mappedFlow.getFinishedAt()).isNull();
    }
  }

  @Nested
  @DisplayName("failIfNotTerminal")
  class FailIfNotTerminal {

    private static final Set<EntityExecutionStatus> NON_TERMINAL =
      EnumSet.of(EntityExecutionStatus.QUEUED, EntityExecutionStatus.IN_PROGRESS);

    @Test
    void positive() {
      when(flowRepository.updateStatusIfCurrentIn(
        eq(FLOW_ID), eq(EntityExecutionStatus.FAILED), eq(NON_TERMINAL), any())).thenReturn(1);
      when(applicationFlowService.failNonTerminalFlows(eq(FLOW_ID), any())).thenReturn(2);
      when(flowStageService.failNonTerminalStages(eq(FLOW_ID), any())).thenReturn(3);

      var result = flowService.failIfNotTerminal(FLOW_ID);

      assertThat(result).isTrue();
    }

    @Test
    void positive_flowIsAlreadyInTerminalStatus() {
      when(flowRepository.updateStatusIfCurrentIn(
        eq(FLOW_ID), eq(EntityExecutionStatus.FAILED), eq(NON_TERMINAL), any())).thenReturn(0);

      var result = flowService.failIfNotTerminal(FLOW_ID);

      assertThat(result).isFalse();
    }

    @Test
    void positive_sameFinishedAtIsUsedForAllUpdates() {
      var flowTimestamp = ArgumentCaptor.forClass(ZonedDateTime.class);
      var applicationFlowTimestamp = ArgumentCaptor.forClass(ZonedDateTime.class);
      var stageTimestamp = ArgumentCaptor.forClass(ZonedDateTime.class);

      when(flowRepository.updateStatusIfCurrentIn(
        eq(FLOW_ID), eq(EntityExecutionStatus.FAILED), eq(NON_TERMINAL), flowTimestamp.capture())).thenReturn(1);
      when(applicationFlowService.failNonTerminalFlows(eq(FLOW_ID), applicationFlowTimestamp.capture()))
        .thenReturn(1);
      when(flowStageService.failNonTerminalStages(eq(FLOW_ID), stageTimestamp.capture())).thenReturn(1);

      flowService.failIfNotTerminal(FLOW_ID);

      assertThat(applicationFlowTimestamp.getValue()).isEqualTo(flowTimestamp.getValue());
      assertThat(stageTimestamp.getValue()).isEqualTo(flowTimestamp.getValue());
    }
  }

  @Nested
  @DisplayName("findStatus")
  class FindStatus {

    @Test
    void positive() {
      when(flowRepository.findStatusById(FLOW_ID)).thenReturn(Optional.of(EntityExecutionStatus.FINISHED));

      var result = flowService.findStatus(FLOW_ID);

      assertThat(result).contains(FINISHED);
    }

    @Test
    void positive_flowNotFound() {
      when(flowRepository.findStatusById(FLOW_ID)).thenReturn(Optional.empty());

      var result = flowService.findStatus(FLOW_ID);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("getTopLevelFlow")
  class GetTopLevelFlow {

    @Test
    void positive_whenIdIsApplicationFlowId() {
      var appFlow = new ApplicationFlow().id(APPLICATION_FLOW_ID).flowId(FLOW_ID);
      var flowEnt = flowEntity();
      var expectedFlow = flow();

      when(applicationFlowService.findById(APPLICATION_FLOW_ID)).thenReturn(Optional.of(appFlow));
      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEnt);
      when(flowMapper.map(flowEnt)).thenReturn(expectedFlow);

      var result = flowService.getTopLevelFlow(APPLICATION_FLOW_ID);

      assertThat(result).isEqualTo(expectedFlow);
    }

    @Test
    void positive_whenIdIsFlowId() {
      var flowEnt = flowEntity();
      var expectedFlow = flow();

      when(applicationFlowService.findById(FLOW_ID)).thenReturn(Optional.empty());
      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEnt);
      when(flowMapper.map(flowEnt)).thenReturn(expectedFlow);

      var result = flowService.getTopLevelFlow(FLOW_ID);

      assertThat(result).isEqualTo(expectedFlow);
    }
  }

  @Nested
  @DisplayName("finishFlowIfNoActiveStages")
  class FinishFlowIfNoActiveStages {

    @Test
    void positive() {
      var finishedAt = ZonedDateTime.now();

      when(flowRepository.updateStatusByIdIfCurrentInAndNoStagesWithStatus(
        FLOW_ID, EntityExecutionStatus.FINISHED, Set.of(EntityExecutionStatus.IN_PROGRESS), finishedAt))
        .thenReturn(1);

      var result = flowService.finishFlowIfNoActiveStages(FLOW_ID, finishedAt);

      assertThat(result).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("failActiveFlow")
  class FailActiveFlow {

    @Test
    void positive() {
      var finishedAt = ZonedDateTime.now();

      when(flowRepository.updateStatusIfCurrentIn(
        FLOW_ID, EntityExecutionStatus.FAILED, Set.of(EntityExecutionStatus.IN_PROGRESS), finishedAt))
        .thenReturn(1);

      var result = flowService.failActiveFlow(FLOW_ID, finishedAt);

      assertThat(result).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("findStaleAsyncFlowIds")
  class FindStaleAsyncFlowIds {

    @Test
    void positive() {
      var cutoff = ZonedDateTime.now();

      when(flowRepository.findIdsAwaitingAsyncBefore(EntityExecutionStatus.IN_PROGRESS, cutoff))
        .thenReturn(List.of(FLOW_ID));

      var result = flowService.findStaleAsyncFlowIds(cutoff);

      assertThat(result).containsExactly(FLOW_ID);
    }
  }

  static class TestValues {

    static FlowEntity flowEntity() {
      return flowEntity(null, null);
    }

    static FlowEntity flowEntity(ZonedDateTime startedAt, ZonedDateTime finishedAt) {
      var entity = new FlowEntity();
      entity.setId(FLOW_ID);
      entity.setType(EntityFlowEntitlementType.ENTITLE);
      entity.setStatus(EntityExecutionStatus.FINISHED);
      entity.setStartedAt(startedAt);
      entity.setFinishedAt(finishedAt);
      return entity;
    }

    static Flow flow() {
      return flow(FINISHED, null, null);
    }

    static Flow flow(ExecutionStatus status, Date startedAt, Date finishedAt) {
      return new Flow()
        .id(FLOW_ID)
        .type(ENTITLE)
        .status(status)
        .startedAt(startedAt)
        .finishedAt(finishedAt);
    }

    static ApplicationFlow applicationFlow() {
      return new ApplicationFlow()
        .id(FLOW_ID)
        .type(EntitlementType.ENTITLE)
        .applicationId(APPLICATION_ID)
        .tenantId(TENANT_ID)
        .status(FINISHED);
    }
  }
}

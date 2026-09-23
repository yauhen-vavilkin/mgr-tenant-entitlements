package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_FENCE_TOKEN;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_ROOT_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.flow.FlowCompletionService;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.entitlement.utils.TransactionHelper;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitleApplicationFlowFinalizerTest {

  @InjectMocks private EntitleApplicationFlowFinalizer flowFinalizer;

  @Mock private FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider;
  @Mock private EntitlementCrudService entitlementCrudService;
  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Mock private FlowRepository flowRepository;
  @Mock private FlowStageRepository stageRepository;
  @Mock private ThreadLocalModuleStageContext threadLocalModuleStageContext;
  @Mock private TransactionHelper transactionHelper;
  @Mock private FlowCompletionService flowCompletionService;

  @BeforeEach
  void setUp() {
    flowFinalizer.setStageRepository(stageRepository);
    flowFinalizer.setFlowRepository(flowRepository);
    flowFinalizer.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
    flowFinalizer.setFlowCompletionService(flowCompletionService);
    flowFinalizer.setRootFlowRepository(flowRepository);
    flowFinalizer.setTransactionHelper(transactionHelper);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService).save(entitlement(TENANT_ID, APPLICATION_ID));
  }

  @Test
  void execute_positive_flowAlreadyTerminal_entitlementNotSaved() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService, never()).save(any());
  }

  @Test
  void execute_positive_inProgressStatus_updateNotCalledButEntitlementSaved() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.IN_PROGRESS);
    when(applicationFlowRepository.markAwaitingAsync(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class))).thenReturn(1);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.execute(stageContext);

    verify(applicationFlowRepository, never()).updateStatusIfCurrentIn(any(), any(), any(), any());
    verify(entitlementCrudService).save(entitlement(TENANT_ID, APPLICATION_ID));
  }

  @Test
  void execute_staleOwnerDoesNotSaveEntitlement() {
    when(flowRepository.guardFenceToken(FLOW_ID, 2L)).thenReturn(0);

    flowFinalizer.execute(fencedStageContext(2L));

    verify(applicationFlowRepository, never()).updateStatusIfCurrentInAndFenceToken(
      any(), any(), any(), any(), any());
    verify(entitlementCrudService, never()).save(any());
  }

  @Test
  void execute_zeroRowFencedStatusWriteDoesNotSaveEntitlement() {
    when(flowRepository.guardFenceToken(FLOW_ID, 2L)).thenReturn(1);
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(applicationFlowRepository.updateStatusIfCurrentInAndFenceToken(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class), eq(2L)))
      .thenReturn(0);

    flowFinalizer.execute(fencedStageContext(2L));

    verify(applicationFlowRepository).updateStatusIfCurrentInAndFenceToken(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class), eq(2L));
    verify(entitlementCrudService, never()).save(any());
  }

  @Test
  void execute_zeroRowFencedAsyncAnchorWriteDoesNotSaveEntitlement() {
    when(flowRepository.guardFenceToken(FLOW_ID, 2L)).thenReturn(1);
    when(applicationFlowRepository.markAwaitingAsyncWithFenceToken(
      eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class), eq(2L))).thenReturn(0);
    var currentRoot = new org.folio.entitlement.domain.entity.FlowEntity();
    currentRoot.setFenceToken(3L);
    when(flowRepository.findById(FLOW_ID)).thenReturn(java.util.Optional.of(currentRoot));
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.IN_PROGRESS);

    flowFinalizer.execute(fencedStageContext(2L));

    verify(applicationFlowRepository).markAwaitingAsyncWithFenceToken(
      eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class), eq(2L));
    verify(entitlementCrudService, never()).save(any());
  }

  @Test
  void cancel_staleOwnerDoesNotDeleteEntitlement() {
    when(flowRepository.guardFenceToken(FLOW_ID, 2L)).thenReturn(0);

    flowFinalizer.cancel(fencedStageContext(2L));

    verify(entitlementCrudService, never()).delete(any());
  }

  @Test
  void onSuccess_positive() {
    var expectedKey = FlowStageKey.of(APPLICATION_FLOW_ID, "EntitleApplicationFlowFinalizer");
    var entity = new FlowStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(any(FlowStageEntity.class))).thenReturn(entity);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.onSuccess(stageContext);

    verify(stageRepository).getReferenceById(expectedKey);
    verify(stageRepository).save(entity);
    verify(threadLocalModuleStageContext).clear();
    verify(transactionHelper).executeAfterCommitInNewTrx(any());
  }

  @Test
  void execute_cancel_positive() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    flowFinalizer.cancel(stageContext);
    verify(entitlementCrudService).delete(entitlement(TENANT_ID, APPLICATION_ID));
  }

  private static ApplicationStageContext fencedStageContext(long token) {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var parameters = new java.util.HashMap<String, Object>();
    parameters.putAll((java.util.Map) flowParameters(request, TestValues.appDescriptor()));
    parameters.put(PARAM_ROOT_FLOW_ID, FLOW_ID);
    parameters.put(PARAM_FENCE_TOKEN, token);
    return appStageContext(FLOW_STAGE_ID, parameters, emptyMap());
  }
}

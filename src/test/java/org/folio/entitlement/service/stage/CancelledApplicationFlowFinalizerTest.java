package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_FENCE_TOKEN;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_ROOT_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Map;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CancelledApplicationFlowFinalizerTest {

  @InjectMocks private CancelledApplicationFlowFinalizer entitlementFlowFinalizer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Mock private FlowRepository flowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(CANCELLED), eq(EnumSet.allOf(EntityExecutionStatus.class)),
      any(ZonedDateTime.class))).thenReturn(1);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    entitlementFlowFinalizer.execute(stageContext);

    verify(applicationFlowRepository).updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(CANCELLED), eq(EnumSet.allOf(EntityExecutionStatus.class)),
      any(ZonedDateTime.class));
  }

  @Test
  void execute_staleOwnerDoesNotUpdateApplicationFlow() {
    when(flowRepository.guardFenceToken(FLOW_ID, 2L)).thenReturn(0);

    var parameters = new java.util.HashMap<String, Object>();
    parameters.put(PARAM_REQUEST, EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build());
    parameters.put(PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    parameters.put(PARAM_ROOT_FLOW_ID, FLOW_ID);
    parameters.put(PARAM_FENCE_TOKEN, 2L);

    entitlementFlowFinalizer.setFlowRepository(flowRepository);
    entitlementFlowFinalizer.execute(appStageContext(FLOW_STAGE_ID, parameters, Map.of()));

    verify(applicationFlowRepository, org.mockito.Mockito.never()).updateStatusIfCurrentInAndFenceToken(
      any(), any(), any(), any(), any());
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest, PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
  }
}

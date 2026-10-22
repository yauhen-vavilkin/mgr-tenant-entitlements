package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.InstanceContext;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit evidence only (mocks): does not prove database persistence or Liquibase migration. */
@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowInitializerTest {

  @InjectMocks private FlowInitializer flowInitializer;
  @Mock private FlowService flowService;
  @Mock private InstanceContext instanceContext;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_passesCurrentInstanceIdToFlowService() {
    var tenantId = UUID.randomUUID();
    var request = EntitlementRequest.builder().tenantId(tenantId).type(EntitlementRequestType.ENTITLE).build();
    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), Map.of());
    when(instanceContext.getInstanceId()).thenReturn("instance-1");

    flowInitializer.execute(context);

    var captor = ArgumentCaptor.forClass(Flow.class);
    verify(flowService).create(captor.capture(), eq("instance-1"));
    assertThat(captor.getValue().getId()).isEqualTo(FLOW_ID);
    assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
  }
}

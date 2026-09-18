package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.service.instance.InstanceContext;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
  void execute_positive_stampsOwnerInstanceId() {
    var ownerInstanceId = UUID.randomUUID();
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(ENTITLE).build();
    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), Map.of());
    when(instanceContext.getInstanceId()).thenReturn(ownerInstanceId);
    var flowCaptor = ArgumentCaptor.forClass(Flow.class);

    flowInitializer.execute(context);

    verify(flowService).create(flowCaptor.capture());
    assertThat(flowCaptor.getValue())
      .extracting(Flow::getId, Flow::getTenantId, Flow::getOwnerInstanceId, Flow::getStatus, Flow::getType)
      .containsExactly(FLOW_ID, TENANT_ID, ownerInstanceId, IN_PROGRESS, ENTITLE);
  }
}

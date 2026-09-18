package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.entitlement.configuration.InstanceIdContext;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.TestConstants;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
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
  @Mock private InstanceIdContext instanceIdContext;

  @Test
  void execute_stampsOwnerInstanceId() {
    var instanceId = UUID.randomUUID();
    var request = EntitlementRequest.builder()
      .tenantId(TestConstants.TENANT_ID)
      .applications(java.util.List.of(TestConstants.APPLICATION_ID))
      .type(org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE)
      .build();
    var context = TestValues.commonStageContext(TestConstants.FLOW_ID,
      java.util.Map.of(CommonStageContext.PARAM_REQUEST, request), java.util.Map.of());
    when(instanceIdContext.getInstanceId()).thenReturn(instanceId);
    var flowCaptor = ArgumentCaptor.forClass(Flow.class);

    flowInitializer.execute(context);

    verify(flowService).create(flowCaptor.capture());
    assertThat(flowCaptor.getValue().getOwnerInstanceId()).isEqualTo(instanceId);
  }
}

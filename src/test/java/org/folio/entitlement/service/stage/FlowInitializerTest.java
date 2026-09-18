package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.configuration.InstanceIdContext;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.EntitlementRequest;
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

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowInitializerTest {

  @InjectMocks private FlowInitializer flowInitializer;

  @Mock private FlowService flowService;
  @Mock private InstanceIdContext instanceIdContext;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_setsOwnerInstanceId() {
    var instanceId = UUID.randomUUID();
    var request = EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .type(EntitlementRequestType.ENTITLE)
      .applications(List.of())
      .build();
    when(instanceIdContext.getInstanceId()).thenReturn(instanceId);
    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), Map.of());

    flowInitializer.execute(context);

    var flowCaptor = ArgumentCaptor.forClass(Flow.class);
    verify(flowService).create(flowCaptor.capture());
    verify(instanceIdContext).getInstanceId();
    assertThat(flowCaptor.getValue().getOwnerInstanceId()).isEqualTo(instanceId);
  }
}

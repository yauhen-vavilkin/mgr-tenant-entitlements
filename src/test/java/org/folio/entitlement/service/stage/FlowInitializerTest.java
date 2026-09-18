package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  private static final UUID INSTANCE_ID = UUID.randomUUID();

  @InjectMocks private FlowInitializer flowInitializer;

  @Mock private FlowService flowService;
  @Mock private InstanceIdContext instanceIdContext;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_setsCurrentInstanceAsOwner() {
    var request = EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .type(EntitlementRequestType.ENTITLE)
      .applications(java.util.List.of())
      .build();
    var context = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), Map.of());
    var flowCaptor = ArgumentCaptor.forClass(Flow.class);
    when(instanceIdContext.getInstanceId()).thenReturn(INSTANCE_ID);

    flowInitializer.execute(context);

    verify(flowService).create(flowCaptor.capture());
    assertThat(flowCaptor.getValue().getOwnerInstanceId()).isEqualTo(INSTANCE_ID);
  }
}

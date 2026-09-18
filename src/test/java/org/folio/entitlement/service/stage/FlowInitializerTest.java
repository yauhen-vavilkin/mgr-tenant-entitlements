package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
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

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var request = EntitlementRequest.builder()
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .type(EntitlementRequestType.ENTITLE)
      .build();
    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), Map.of());
    var flowCaptor = ArgumentCaptor.forClass(Flow.class);

    flowInitializer.execute(stageContext);

    verify(flowService).create(flowCaptor.capture());
    var createdFlow = flowCaptor.getValue();
    assertThat(createdFlow.getId()).isEqualTo(FLOW_ID);
    assertThat(createdFlow.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(createdFlow.getType()).isEqualTo(EntitlementRequestType.ENTITLE);
    assertThat(createdFlow.getStatus()).isEqualTo(IN_PROGRESS);
    assertThat(createdFlow.getStartedAt()).isNotNull();
  }
}

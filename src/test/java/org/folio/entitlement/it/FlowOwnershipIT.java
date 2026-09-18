package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.service.InstanceContext;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.TestConstants;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
})
class FlowOwnershipIT extends BaseIntegrationTest {

  private static final UUID NEW_FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-000000000001");
  private static final UUID LEGACY_FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-0000000000aa");
  private static final UUID OWNED_FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-0000000000bb");
  private static final UUID APPLICATION_FLOW_ID = UUID.fromString("aa000000-0000-0000-0000-0000000000cc");
  private static final UUID OWNER_INSTANCE_ID = UUID.fromString("aa000000-0000-0000-0000-0000000000ff");

  @Autowired private FlowService flowService;
  @Autowired private InstanceContext instanceContext;

  @Test
  void createFlow_positive_flowIsOwnedByCurrentInstance() {
    var flow = new Flow()
      .id(NEW_FLOW_ID)
      .tenantId(TENANT_ID)
      .type(ENTITLE)
      .status(IN_PROGRESS);

    flowService.create(flow);

    assertThat(flowService.findOwnerInstanceId(NEW_FLOW_ID)).contains(instanceContext.getInstanceId());
  }

  @Test
  @Sql("classpath:/sql/ownership/flows.sql")
  void findOwnerInstanceId_positive_ownershipIsResolvedThroughParentFlow() {
    assertThat(flowService.findOwnerInstanceId(OWNED_FLOW_ID)).contains(OWNER_INSTANCE_ID);
    assertThat(flowService.findOwnerInstanceId(APPLICATION_FLOW_ID)).contains(OWNER_INSTANCE_ID);
  }

  @Test
  @Sql("classpath:/sql/ownership/flows.sql")
  void findOwnerInstanceId_positive_legacyFlowWithoutOwnerIsReadable() throws Exception {
    assertThat(flowService.findOwnerInstanceId(LEGACY_FLOW_ID)).isEmpty();

    mockMvc.perform(get("/entitlement-flows/{flowId}", LEGACY_FLOW_ID)
        .header(TOKEN, TestConstants.OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id", is(LEGACY_FLOW_ID.toString())))
      .andExpect(jsonPath("$.status", is("in_progress")));
  }
}

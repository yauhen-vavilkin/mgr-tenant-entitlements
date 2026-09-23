package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.entitlement.controller.ApiExceptionHandler.FLOW_ID_HEADER;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.awaitility.Awaitility;
import org.folio.entitlement.service.InstanceContext;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Verifies the poison path of the flow execution timeout: with a 1ms timeout the synchronous request (almost always)
 * times out before the engine has run the FlowInitializer stage, so no flow row exists yet - a FAILED row is inserted
 * instead, and the background flow must refuse to start from it, never installing or entitling anything.
 */
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
  "application.flow-engine.execution-timeout=1ms",
  "application.clients.folio.connect-timeout=60s",
  "application.clients.folio.read-timeout=60s"
})
class FlowExecutionImmediateTimeoutIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";

  @Autowired
  private InstanceContext instanceContext;

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json"
  })
  void desiredState_negative_timeoutBeforeFlowStarted() throws Exception {
    var request = put("/entitlements/state")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(desiredStateRequest(TENANT_ID, FOLIO_APP1_ID)))
      .queryParam("tenantParameters", "loadReference=true")
      .queryParam("ignoreErrors", "true");

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(header().exists(FLOW_ID_HEADER))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionTimeoutException")))
      .andReturn();

    var flowId = mvcResult.getResponse().getHeader(FLOW_ID_HEADER);

    // valid for both race outcomes: the poison-inserted FAILED row and the regular conditional update read the same
    getFlow(flowId)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.ownerInstanceId", is(instanceContext.getInstanceId().toString())));

    // the flow finalizer stage row is the background completion beacon: FailedFlowFinalizer when the flow refused to
    // start, FinishedFlowFinalizer when the initializer won the race and the flow ran through with skipped finalizers
    Awaitility.await().atMost(30, SECONDS).untilAsserted(() -> {
      getFlow(flowId)
        .andExpect(jsonPath("$.status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[*].status", everyItem(is("failed"))))
        .andExpect(jsonPath("$.stages[?(@.status == 'finished')].name",
          hasItem(oneOf("FailedFlowFinalizer", "FinishedFlowFinalizer"))));
      getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
    });
  }

  private static ResultActions getFlow(String flowId) throws Exception {
    return mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .queryParam("includeStages", "true")
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk());
  }
}

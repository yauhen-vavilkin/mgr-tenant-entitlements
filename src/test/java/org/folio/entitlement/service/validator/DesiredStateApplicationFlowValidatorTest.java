package org.folio.entitlement.service.validator;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowRecoveryService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateApplicationFlowValidatorTest {

  private static final String APP1_ID = "app1-1.0.0";
  private static final String APP2_ID = "app2-1.0.0";

  @InjectMocks private DesiredStateApplicationFlowValidator validator;
  @Mock private ApplicationFlowService applicationFlowService;
  @Mock private FlowRecoveryService flowRecoveryService;

  @AfterEach
  void tearDown() {
    verify(flowRecoveryService, atLeast(0)).recover(any());
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_entitleBucketWithNoExistingFlows() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), emptySet(), emptySet());
    var context = createContext(plan);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(Collections.emptyList());

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_revokeBucketWithFinishedEntitleFlow() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID));
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, ENTITLE, FINISHED);

    when(applicationFlowService.findLastFlows(Set.of(APP1_ID), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_upgradeBucketWithFinishedEntitleFlow() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_ID), emptySet());
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, ENTITLE, FINISHED);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_multipleBuckets() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), Set.of(APP2_ID), emptySet());
    var context = createContext(plan);
    var app2ExistingFlow = createApplicationFlow(APP2_ID, ENTITLE, FINISHED);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(Collections.emptyList());
    when(applicationFlowService.findLastFlowsByNames(List.of("app2"), TENANT_ID))
      .thenReturn(List.of(app2ExistingFlow));

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_allBucketsEmpty() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), emptySet());
    var context = createContext(plan);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_rechecksFlowsAfterRecovery() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), emptySet(), emptySet());
    var context = createContext(plan);
    var staleFlow = createApplicationFlow(APP1_ID, ENTITLE, QUEUED);
    var flowQuery = List.of("app1");

    when(applicationFlowService.findLastFlowsByNames(flowQuery, TENANT_ID))
      .thenReturn(List.of(staleFlow), Collections.emptyList());
    when(flowRecoveryService.recover(List.of(staleFlow))).thenReturn(true);

    assertThatCode(() -> validator.execute(context)).doesNotThrowAnyException();
    verify(applicationFlowService, org.mockito.Mockito.times(2))
      .findLastFlowsByNames(flowQuery, TENANT_ID);
  }

  @Test
  void execute_negative_entitleBucketWithQueuedFlow() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), emptySet(), emptySet());
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, ENTITLE, QUEUED);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Entitle flow is in queue")
        );
      });
  }

  @Test
  void execute_negative_entitleBucketWithInProgressFlow() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), emptySet(), emptySet());
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, ENTITLE, IN_PROGRESS);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Entitle flow is in progress")
        );
      });
  }

  @Test
  void execute_negative_revokeBucketWithQueuedFlow() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID));
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, REVOKE, QUEUED);

    when(applicationFlowService.findLastFlows(Set.of(APP1_ID), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Revoke flow is in queue")
        );
      });
  }

  @Test
  void execute_negative_upgradeBucketWithQueuedUpgradeFlow() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), Set.of(APP1_ID), emptySet());
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, UPGRADE, QUEUED);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Upgrade flow is in queue")
        );
      });
  }

  @Test
  void execute_negative_multipleBucketsWithErrors() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), Set.of(APP2_ID), emptySet());
    var context = createContext(plan);
    var app1Flow = createApplicationFlow(APP1_ID, ENTITLE, QUEUED);
    var app2Flow = createApplicationFlow(APP2_ID, UPGRADE, IN_PROGRESS);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(app1Flow));
    when(applicationFlowService.findLastFlowsByNames(List.of("app2"), TENANT_ID))
      .thenReturn(List.of(app2Flow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactlyInAnyOrder(
          new Parameter().key(APP1_ID).value("Entitle flow is in queue"),
          new Parameter().key(APP2_ID).value("Upgrade flow is in progress")
        );
      });
  }

  @Test
  void execute_negative_entitleBucketWithFinishedEntitleFlow() {
    var plan = ApplicationStateTransitionPlan.of(Set.of(APP1_ID), emptySet(), emptySet());
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, ENTITLE, FINISHED);

    when(applicationFlowService.findLastFlowsByNames(List.of("app1"), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Entitle flow finished")
        );
      });
  }

  @Test
  void execute_negative_revokeBucketWithFinishedRevokeFlow() {
    var plan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), Set.of(APP1_ID));
    var context = createContext(plan);
    var existingFlow = createApplicationFlow(APP1_ID, REVOKE, FINISHED);

    when(applicationFlowService.findLastFlows(Set.of(APP1_ID), TENANT_ID))
      .thenReturn(List.of(existingFlow));

    assertThatThrownBy(() -> validator.execute(context))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in desired state request")
      .satisfies(err -> {
        var exception = (RequestValidationException) err;
        assertThat(exception.getErrorParameters()).containsExactly(
          new Parameter().key(APP1_ID).value("Revoke flow finished")
        );
      });
  }

  private static CommonStageContext createContext(ApplicationStateTransitionPlan plan) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    var context = commonStageContext(FLOW_ID, flowParams, Collections.emptyMap());
    context.withApplicationStateTransitionPlan(plan);
    return context;
  }

  private static ApplicationFlow createApplicationFlow(String applicationId, EntitlementType type,
    ExecutionStatus status) {
    return new ApplicationFlow()
      .applicationId(applicationId)
      .tenantId(TENANT_ID)
      .type(type)
      .status(status);
  }
}

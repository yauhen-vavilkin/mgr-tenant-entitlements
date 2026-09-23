package org.folio.entitlement.service.validator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowRecoveryService;
import org.folio.test.types.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationFlowValidatorTest {

  @InjectMocks private ApplicationFlowValidator validator;
  @Mock private ApplicationFlowService applicationFlowService;
  @Mock private FlowRecoveryService flowRecoveryService;
  @Mock private DesiredStateValidationService desiredStateValidationService;

  @DisplayName("validate_positive_entitleRequest")
  @MethodSource("positiveEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validate_positive_entitleOrUpgradeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationNames = getApplicationNames(request);
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    validator.validate(request);

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("validate_positive_revokeRequest")
  @MethodSource("positiveEntitlementRevokeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validate_positive_revokeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationId = request.getApplications();
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlows(applicationId, tenantId)).thenReturn(applicationFlows);

    validator.validate(request);

    verify(applicationFlowService).findLastFlows(applicationId, tenantId);
  }

  @Test
  void validate_positive_stateRequest_delegatesToDesiredStateValidation() {
    var request = request(STATE);

    validator.validate(request);

    verify(desiredStateValidationService).validate(request);
  }

  @DisplayName("validate_negative_entitleRequest")
  @MethodSource("negativeEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validate_negative_entitleRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .collect(Collectors.toList());
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    assertThatThrownBy(() -> validator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .satisfies(err -> assertThat(((RequestValidationException) err).getErrorParameters()).isEqualTo(expectedParams));

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("validate_negative_upgradeRequest")
  @MethodSource("negativeEntitlementUpgradeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void validate_negative_upgradeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .collect(Collectors.toList());
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    assertThatThrownBy(() -> validator.validate(request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .satisfies(err -> assertThat(((RequestValidationException) err).getErrorParameters()).isEqualTo(expectedParams));

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("execute_positive_entitleRequest")
  @MethodSource("positiveEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_positive_entitleOrUpgradeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationNames = request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .toList();
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    validator.execute(stageContext);

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("execute_positive_revokeRequest")
  @MethodSource("positiveEntitlementRevokeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_positive_revokeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows) {
    var applicationId = request.getApplications();
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlows(applicationId, tenantId)).thenReturn(applicationFlows);

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    validator.execute(stageContext);

    verify(applicationFlowService).findLastFlows(applicationId, tenantId);
  }

  @DisplayName("execute_negative_entitleRequest")
  @MethodSource("negativeEntitlementEntitleFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_negative_entitleRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationNames = getApplicationNames(request);
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    assertThatThrownBy(() -> validator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .extracting(err -> ((RequestValidationException) err).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).isEqualTo(expectedParams));

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("execute_negative_upgradeRequest")
  @MethodSource("negativeEntitlementUpgradeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_negative_upgradeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationNames = getApplicationNames(request);
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlowsByNames(applicationNames, tenantId)).thenReturn(applicationFlows);

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    assertThatThrownBy(() -> validator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .extracting(err -> ((RequestValidationException) err).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).isEqualTo(expectedParams));

    verify(applicationFlowService).findLastFlowsByNames(applicationNames, tenantId);
  }

  @DisplayName("execute_negative_revokeRequest")
  @MethodSource("negativeEntitlementRevokeFlowsDataProvider")
  @ParameterizedTest(name = "[{index}] {0}")
  void execute_negative_revokeRequest(@SuppressWarnings("unused") String name,
    EntitlementRequest request, List<ApplicationFlow> applicationFlows, List<Parameter> expectedParams) {
    var applicationId = request.getApplications();
    var tenantId = request.getTenantId();
    when(applicationFlowService.findLastFlows(applicationId, tenantId)).thenReturn(applicationFlows);

    var stageContext = commonStageContext(FLOW_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    assertThatThrownBy(() -> validator.execute(stageContext))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Found validation errors in entitlement request")
      .extracting(err -> ((RequestValidationException) err).getErrorParameters())
      .satisfies(parameters -> assertThat(parameters).isEqualTo(expectedParams));

    verify(applicationFlowService).findLastFlows(applicationId, tenantId);
  }

  @ParameterizedTest
  @DisplayName("shouldValidate_parameterized")
  @CsvSource({"ENTITLE,true", "REVOKE,true", "UPGRADE,true", "STATE,true", ",true"})
  void shouldValidate_parameterized(EntitlementRequestType type, boolean expected) {
    var request = EntitlementRequest.builder().type(type).build();
    var result = validator.shouldValidate(request);
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> positiveEntitlementEntitleFlowsDataProvider() {
    return Stream.of(
      arguments("Entitle: no application flows found", request(ENTITLE), emptyList()),
      arguments("Entitle: revoked flow found", request(ENTITLE), List.of(flow(EntitlementType.REVOKE, FINISHED))),
      arguments("Entitle: failed entitle flow found", request(ENTITLE), List.of(flow(EntitlementType.ENTITLE, FAILED))),
      arguments("Entitle: cancelled entitle flow found", request(ENTITLE),
        List.of(flow(EntitlementType.ENTITLE, CANCELLED))),

      arguments("Entitle: multiple application flows", request(ENTITLE, "app1-1.0.0", "app2-1.0.0"), emptyList()),
      arguments("Entitle: multiple revoked application flows", request(ENTITLE, "app1-1.0.0", "app2-1.0.0"),
        List.of(flow("app1-1.0.0", EntitlementType.REVOKE, FINISHED),
          flow("app2-1.0.0", EntitlementType.REVOKE, FINISHED))),

      arguments("Upgrade: no application flows found", request(UPGRADE), emptyList()),
      arguments("Upgrade: revoked flow found", request(UPGRADE), List.of(flow(EntitlementType.REVOKE, FINISHED))),
      arguments("Upgrade: finished upgrade flow found", request(UPGRADE),
        List.of(flow(EntitlementType.UPGRADE, FINISHED))),
      arguments("Upgrade: failed upgrade flow found", request(UPGRADE), List.of(flow(EntitlementType.UPGRADE, FAILED))),
      arguments("Entitle: cancelled upgrade flow found", request(UPGRADE),
        List.of(flow(EntitlementType.UPGRADE, CANCELLED))),

      arguments("Revoke: finished entitle flows found", request(UPGRADE),
        List.of(flow(EntitlementType.ENTITLE, FINISHED))),
      arguments("Revoke: failed upgrade flow found", request(UPGRADE), List.of(flow(EntitlementType.UPGRADE, FAILED))),
      arguments("Revoke: cancelled upgrade flow found", request(UPGRADE),
        List.of(flow(EntitlementType.UPGRADE, CANCELLED)))
    );
  }

  private static Stream<Arguments> positiveEntitlementRevokeFlowsDataProvider() {
    return Stream.of(
      arguments("Revoke: no application flows found", request(REVOKE), emptyList()),
      arguments("Revoke: finished entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.ENTITLE, FINISHED))),
      arguments("Revoke: finished upgrade flow found", request(REVOKE),
        List.of(flow(EntitlementType.UPGRADE, FINISHED))),
      arguments("Revoke: failed entitle flow found", request(REVOKE), List.of(flow(EntitlementType.REVOKE, FAILED))),
      arguments("Revoke: cancelled entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.REVOKE, CANCELLED)))
    );
  }

  private static Stream<Arguments> negativeEntitlementEntitleFlowsDataProvider() {
    return Stream.of(
      arguments("Entitle: entitled flow found", request(ENTITLE),
        List.of(flow(EntitlementType.ENTITLE, FINISHED)), List.of(parameter("Entitle flow finished"))),
      arguments("Entitle: queued flow found", request(ENTITLE),
        List.of(flow(EntitlementType.ENTITLE, QUEUED)), List.of(parameter("Entitle flow is in queue"))),
      arguments("Entitle: in progress flow found", request(ENTITLE),
        List.of(flow(EntitlementType.ENTITLE, IN_PROGRESS)), List.of(parameter("Entitle flow is in progress"))),
      arguments("Entitle: queued revoke flow found", request(ENTITLE),
        List.of(flow(EntitlementType.REVOKE, QUEUED)), List.of(parameter("Another revoke flow is in queue"))),
      arguments("Entitle: in progress revoke flow found", request(ENTITLE),
        List.of(flow(EntitlementType.REVOKE, IN_PROGRESS)), List.of(parameter("Another revoke flow is in progress"))),
      arguments("Entitle: failed revoke flow found", request(ENTITLE),
        List.of(flow(EntitlementType.REVOKE, FAILED)), List.of(parameter("Previous revoke flow failed"))),
      arguments("Entitle: cancelled revoke flow found", request(ENTITLE),
        List.of(flow(EntitlementType.REVOKE, CANCELLED)), List.of(parameter("Previous revoke flow canceled"))),
      arguments("Entitle: failed upgrade flow found", request(ENTITLE),
        List.of(flow(EntitlementType.UPGRADE, FAILED)), List.of(parameter("Previous upgrade flow failed"))),
      arguments("Entitle: cancelled upgrade flow found", request(ENTITLE),
        List.of(flow(EntitlementType.UPGRADE, CANCELLED)), List.of(parameter("Previous upgrade flow canceled"))),
      arguments("Entitle: finished upgrade flow found", request(ENTITLE),
        List.of(flow(EntitlementType.UPGRADE, FINISHED)), List.of(parameter("Upgrade flow finished")))
    );
  }

  private static Stream<Arguments> negativeEntitlementRevokeFlowsDataProvider() {
    return Stream.of(
      arguments("Revoke: finished revoke flow found", request(REVOKE),
        List.of(flow(EntitlementType.REVOKE, FINISHED)), List.of(parameter("Revoke flow finished"))),
      arguments("Revoke: queued revoke flow found", request(REVOKE),
        List.of(flow(EntitlementType.REVOKE, QUEUED)), List.of(parameter("Revoke flow is in queue"))),
      arguments("Revoke: in progress revoke flow found", request(REVOKE),
        List.of(flow(EntitlementType.REVOKE, IN_PROGRESS)), List.of(parameter("Revoke flow is in progress"))),
      arguments("Revoke: queued entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.ENTITLE, QUEUED)), List.of(parameter("Another entitle flow is in queue"))),
      arguments("Revoke: in progress entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.ENTITLE, IN_PROGRESS)), List.of(parameter("Another entitle flow is in progress"))),
      arguments("Revoke: failed entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.ENTITLE, FAILED)), List.of(parameter("Previous entitle flow failed"))),
      arguments("Revoke: cancelled entitle flow found", request(REVOKE),
        List.of(flow(EntitlementType.ENTITLE, CANCELLED)), List.of(parameter("Previous entitle flow canceled")))
    );
  }

  private static Stream<Arguments> negativeEntitlementUpgradeFlowsDataProvider() {
    return Stream.of(
      arguments("Upgrade: queued revoke flow found", request(UPGRADE),
        List.of(flow(EntitlementType.REVOKE, QUEUED)), List.of(parameter("Another revoke flow is in queue"))),
      arguments("Revoke: in progress revoke flow found", request(UPGRADE),
        List.of(flow(EntitlementType.REVOKE, IN_PROGRESS)), List.of(parameter("Another revoke flow is in progress"))),

      arguments("Revoke: queued upgrade flow found", request(UPGRADE),
        List.of(flow(EntitlementType.UPGRADE, QUEUED)), List.of(parameter("Upgrade flow is in queue"))),
      arguments("Revoke: in progress upgrade flow found", request(UPGRADE),
        List.of(flow(EntitlementType.UPGRADE, IN_PROGRESS)), List.of(parameter("Upgrade flow is in progress"))),

      arguments("Revoke: queued revoke flow found", request(UPGRADE),
        List.of(flow(EntitlementType.ENTITLE, QUEUED)), List.of(parameter("Another entitle flow is in queue"))),
      arguments("Revoke: in progress revoke flow found", request(UPGRADE),
        List.of(flow(EntitlementType.ENTITLE, IN_PROGRESS)), List.of(parameter("Another entitle flow is in progress"))),
      arguments("Revoke: failed revoke flow found", request(UPGRADE),
        List.of(flow(EntitlementType.ENTITLE, FAILED)), List.of(parameter("Previous entitle flow failed"))),
      arguments("Revoke: cancelled flow found", request(UPGRADE),
        List.of(flow(EntitlementType.ENTITLE, CANCELLED)), List.of(parameter("Previous entitle flow canceled")))
    );
  }

  private static EntitlementRequest request(EntitlementRequestType type) {
    return request(type, APPLICATION_ID);
  }

  private static EntitlementRequest request(EntitlementRequestType type, String... applicationIds) {
    return EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .applications(Arrays.asList(applicationIds))
      .type(type)
      .build();
  }

  private static Parameter parameter(String errorMessage) {
    return new Parameter().key(APPLICATION_ID).value(errorMessage);
  }

  private static ApplicationFlow flow(EntitlementType type, ExecutionStatus status) {
    return flow(APPLICATION_ID, type, status);
  }

  private static ApplicationFlow flow(String applicationId, EntitlementType type, ExecutionStatus status) {
    return new ApplicationFlow().type(type).status(status).applicationId(applicationId).tenantId(TENANT_ID);
  }

  private static @NotNull List<String> getApplicationNames(EntitlementRequest request) {
    return request.getApplications().stream()
      .map(SemverUtils::getName)
      .distinct()
      .toList();
  }
}

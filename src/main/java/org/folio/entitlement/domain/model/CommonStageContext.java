package org.folio.entitlement.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.stage.ApplicationDescriptorTreeLoader;
import org.folio.entitlement.service.stage.ApplicationStateTransitionPlanner;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class CommonStageContext extends IdentifiableStageContext {

  public static final String PARAM_REQUEST = "entitlementRequest";
  public static final String PARAM_FENCE_TOKEN = "fenceToken";
  public static final String PARAM_APP_DESCRIPTORS = "applicationDescriptors";
  public static final String PARAM_QUEUED_APP_FLOWS = "queuedApplicationFlows";
  public static final String PARAM_ENTITLED_APP_IDS = "entitledApplicationIds";
  public static final String PARAM_ENTITLED_APP_DESCRIPTORS = "entitledApplicationDescriptors";
  public static final String PARAM_APP_AND_DEPENDENCY_DESCRIPTORS = "applicationAndDependencyDescriptors";
  public static final String PARAM_TENANT_NAME = "tenantName";
  public static final String PARAM_APP_STATE_TRANSITION_PLAN = "applicationStateTransitionPlan";
  public static final String PARAM_APP_STATE_TRANSITION_DESCRIPTORS = "applicationStateTransitionDescriptors";

  /**
   * Creates {@link CommonStageContext} from {@link StageContext}.
   *
   * @param stageContext - {@link StageContext} object
   */
  public CommonStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Returns entitlement request from {@link StageContext} object.
   */
  public EntitlementRequest getEntitlementRequest() {
    return context.getFlowParameter(PARAM_REQUEST);
  }

  /**
   * Decorates {@link StageContext} with {@link CommonStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link CommonStageContext} decorator object
   */
  public static CommonStageContext decorate(StageContext stageContext) {
    return new CommonStageContext(stageContext);
  }

  /**
   * Returns loaded tenant name.
   *
   * @return tenant name as {@link String}
   */
  public String getTenantName() {
    return context.get(PARAM_TENANT_NAME);
  }

  /**
   * Returns all application descriptors in request.
   *
   * @return list with loaded application descriptors
   */
  public List<ApplicationDescriptor> getApplicationDescriptors() {
    return context.get(PARAM_APP_DESCRIPTORS);
  }

  /**
   * Returns all entitled application descriptors for upgrade request.
   *
   * @return list with loaded entitled application descriptors
   */
  public List<ApplicationDescriptor> getEntitledApplicationDescriptors() {
    return context.get(PARAM_ENTITLED_APP_DESCRIPTORS);
  }

  /**
   * Returns all application descriptors in request and all dependent application descriptors.
   *
   * <p>Application descriptors are loaded in {@link ApplicationDescriptorTreeLoader} stage</p>
   *
   * @return list with parent application descriptors and their dependencies
   */
  public List<ApplicationDescriptor> getApplicationAndDependencyDescriptors() {
    return context.get(PARAM_APP_AND_DEPENDENCY_DESCRIPTORS);
  }

  /**
   * Returns a map where key is application id, and value is the corresponding UUID for application flow with QUEUED
   * status.
   *
   * @return application-id to queued application flow id map
   */
  public Map<String, UUID> getQueuedApplicationFlows() {
    return context.get(PARAM_QUEUED_APP_FLOWS);
  }

  /**
   * Returns entitled application ids (after validation) for upgrade request.
   *
   * @return {@link List} with entitled application ids as {@link String}
   */
  public List<String> getEntitledApplicationIds() {
    return context.get(PARAM_ENTITLED_APP_IDS);
  }

  /**
   * Returns application state transition plan. The transition plan is created during
   * "Desired State" request processing only by dedicated stage: {@link ApplicationStateTransitionPlanner}.
   *
   * @return {@link ApplicationStateTransitionPlan} object
   */
  public ApplicationStateTransitionPlan getApplicationStateTransitionPlan() {
    return context.get(PARAM_APP_STATE_TRANSITION_PLAN);
  }

  /**
   * Returns application descriptors per entitlement type for "Desired State" request processing.
   *
   * @return map where key is entitlement type, value is list of application descriptors
   */
  public Map<EntitlementType, List<ApplicationDescriptor>> getApplicationStateTransitionDescriptors() {
    return context.get(PARAM_APP_STATE_TRANSITION_DESCRIPTORS);
  }

  /**
   * Adds loaded tenant name to {@link StageContext}.
   *
   * @param tenantName - loaded tenant name from {@code mgr-tenants}
   * @return {@link CommonStageContext} with tenant name set
   */
  public CommonStageContext withTenantName(String tenantName) {
    context.put(PARAM_TENANT_NAME, tenantName);
    return this;
  }

  /**
   * Sets all application descriptors in request and, optionally, all dependent application descriptors.
   *
   * <p>Application descriptors are loaded
   * in {@link ApplicationDescriptorTreeLoader} stage</p>
   *
   * @return {@link CommonStageContext} with application descriptors set
   */
  public CommonStageContext withApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
    context.put(PARAM_APP_DESCRIPTORS, applicationDescriptors);
    return this;
  }

  /**
   * Sets all application descriptors in request and all dependent application descriptors.
   *
   * <p>Application descriptors are loaded
   * in {@link ApplicationDescriptorTreeLoader} stage</p>
   *
   * @return {@link CommonStageContext} with application descriptors set
   */
  public CommonStageContext withEntitledApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
    context.put(PARAM_ENTITLED_APP_DESCRIPTORS, applicationDescriptors);
    return this;
  }

  /**
   * Sets queued application flow ids per application id as {@link Map}.
   *
   * @param queuedApplicationFlows - queued application flow ids map
   * @return {@link CommonStageContext} with queued application flows set
   */
  public CommonStageContext withQueuedApplicationFlows(Map<String, UUID> queuedApplicationFlows) {
    context.put(PARAM_QUEUED_APP_FLOWS, queuedApplicationFlows);
    return this;
  }

  /**
   * Sets entitled application ids for upgrade flow.
   *
   * @param entitledApplicationIds - queued application flow ids map
   * @return {@link CommonStageContext} with entitled application ids set
   */
  public CommonStageContext withEntitledApplicationIds(List<String> entitledApplicationIds) {
    context.put(PARAM_ENTITLED_APP_IDS, entitledApplicationIds);
    return this;
  }

  /**
   * Sets all application descriptors in request and all dependent application descriptors.
   *
   * <p>Application descriptors are loaded in {@link ApplicationDescriptorTreeLoader} stage</p>
   *
   * @param descriptors - list of parent application descriptors
   * @return {@link CommonStageContext} with parent application descriptors set
   */
  public CommonStageContext withApplicationAndDependencyDescriptors(List<ApplicationDescriptor> descriptors) {
    context.put(PARAM_APP_AND_DEPENDENCY_DESCRIPTORS, descriptors);
    return this;
  }

  /**
   * Sets application state transition plan.
   *
   * @param transitionPlan - application state transition plan
   * @return {@link CommonStageContext} with application state transition plan set
   */
  public CommonStageContext withApplicationStateTransitionPlan(ApplicationStateTransitionPlan transitionPlan) {
    context.put(PARAM_APP_STATE_TRANSITION_PLAN, transitionPlan);
    return this;
  }

  /**
   * Sets application descriptors per entitlement type for "Desired State" request processing.
   *
   * @param descriptors - map where key is entitlement type, value is list of application descriptors
   * @return {@link CommonStageContext} with application state transition descriptors set
   */
  public CommonStageContext withApplicationStateTransitionDescriptors(
    Map<EntitlementType, List<ApplicationDescriptor>> descriptors) {
    context.put(PARAM_APP_STATE_TRANSITION_DESCRIPTORS, descriptors);
    return this;
  }

  /**
   * Clears context from application descriptors and other objects
   * after preparation of dedicated flow for each application.
   */
  public void clearContext() {
    context.remove(PARAM_APP_DESCRIPTORS);
    context.remove(PARAM_QUEUED_APP_FLOWS);
    context.remove(PARAM_ENTITLED_APP_IDS);
    context.remove(PARAM_APP_AND_DEPENDENCY_DESCRIPTORS);
    context.remove(PARAM_ENTITLED_APP_DESCRIPTORS);
    context.remove(PARAM_APP_STATE_TRANSITION_PLAN);
    context.remove(PARAM_APP_STATE_TRANSITION_DESCRIPTORS);
  }
}

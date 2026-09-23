package org.folio.entitlement.integration.folio.flow;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_FENCE_TOKEN;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_ROOT_FLOW_ID;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ENTITLEMENT_TYPE;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ID;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_TYPE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toUnmodifiableMap;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.domain.model.Sequence;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.ModuleSequenceProvider;
import org.folio.entitlement.service.flow.ModuleFlowFactory;
import org.folio.entitlement.service.flow.ModulesFlowProvider;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

public class FolioModulesFlowProvider implements ModulesFlowProvider {

  private final Map<EntitlementType, ModuleFlowFactory> moduleFlowFactories;
  private final ModuleSequenceProvider moduleSequenceProvider;
  private final Executor moduleInstallerExecutor;

  /**
   * Creates {@link FolioModulesFlowProvider} from list of {@link ModuleFlowFactory} beans.
   *
   * @param moduleFlowFactories - {@link ModuleFlowFactory} beans
   * @param moduleSequenceProvider - module sequence provider
   * @param moduleInstallerExecutor - executor for module installation parallel stages
   */
  public FolioModulesFlowProvider(List<ModuleFlowFactory> moduleFlowFactories,
    ModuleSequenceProvider moduleSequenceProvider, Executor moduleInstallerExecutor) {
    this.moduleSequenceProvider = moduleSequenceProvider;
    this.moduleFlowFactories = toUnmodifiableMap(moduleFlowFactories, ModuleFlowFactory::getEntitlementType);
    this.moduleInstallerExecutor = moduleInstallerExecutor;
  }

  @Override
  public Flow createFlow(StageContext stageContext) {
    var ctx = new ApplicationStageContext(stageContext);
    var request = ctx.getEntitlementRequest();
    var type = capitalize(ctx.getEntitlementType().getValue());
    var flowId = ctx.flowId() + "/FolioModules" + capitalize(type) + "Flow";
    var flowBuilder = Flow.builder().id(flowId).executionStrategy(request.getExecutionStrategy());

    var sequence = Sequence.withPrefix(flowId + "/Level-");
    var moduleSequence = moduleSequenceProvider.getSequence(ctx, MODULE);
    getModuleStages(sequence, ctx, MODULE, moduleSequence.moduleDescriptors()).forEach(flowBuilder::stage);

    var uiModuleSequence = moduleSequenceProvider.getSequence(ctx, UI_MODULE);
    getModuleStages(sequence, ctx, UI_MODULE, uiModuleSequence.moduleDescriptors()).forEach(flowBuilder::stage);

    var deprecatedSequence = Sequence.withPrefix(flowId + "/Deprecated/Level-");
    var deprecatedDescriptors = moduleSequence.deprecatedModuleDescriptors();
    getDeprecatedModulesStages(deprecatedSequence, MODULE, ctx, deprecatedDescriptors).forEach(flowBuilder::stage);

    var deprecatedUiDescriptors = uiModuleSequence.deprecatedModuleDescriptors();
    getDeprecatedModulesStages(deprecatedSequence, UI_MODULE, ctx, deprecatedUiDescriptors).forEach(flowBuilder::stage);

    return flowBuilder.build();
  }

  private List<? extends Stage<? extends StageContext>> getModuleStages(Sequence sequence, ApplicationStageContext ctx,
    ModuleType moduleType, List<List<ModuleDescriptorHolder>> moduleDescriptorsSequence) {
    var result = new ArrayList<Stage<? extends StageContext>>();
    for (var moduleDescriptorHolders : moduleDescriptorsSequence) {
      var stageId = sequence.nextValue();
      var stages = moduleDescriptorHolders.stream()
        .map(h -> getModuleFlow(stageId, ctx, moduleType, h.moduleDescriptor(), h.installedModuleDescriptor()))
        .toList();

      result.add(combineStages(stageId, stages, moduleInstallerExecutor));
    }

    return result;
  }

  private Flow getModuleFlow(String flowId, ApplicationStageContext ctx, ModuleType moduleType,
    ModuleDescriptor descriptor, ModuleDescriptor installedModuleDescriptor) {
    var moduleFlowFactory = Objects.requireNonNull(moduleFlowFactories.get(ctx.getEntitlementType()));
    var flowParameters = getModuleFlowParameters(ctx, moduleType, descriptor, installedModuleDescriptor);
    var moduleFlowId = flowId + "/" + flowParameters.get(PARAM_MODULE_ID);
    var request = ctx.getEntitlementRequest();
    return moduleType == MODULE
      ? moduleFlowFactory.createModuleFlow(moduleFlowId, request.getExecutionStrategy(), flowParameters)
      : moduleFlowFactory.createUiModuleFlow(moduleFlowId, request.getExecutionStrategy(), flowParameters);
  }

  private List<? extends Stage<? extends StageContext>> getDeprecatedModulesStages(Sequence sequence,
    ModuleType moduleType, ApplicationStageContext context, List<List<ModuleDescriptor>> moduleDescriptorsSequence) {
    return moduleDescriptorsSequence.stream()
      .map(descriptors -> getDeprecatedModulesFlow(sequence, context, moduleType, descriptors))
      .toList();
  }

  private Stage<? extends StageContext> getDeprecatedModulesFlow(Sequence sequence, ApplicationStageContext context,
    ModuleType moduleType, List<ModuleDescriptor> descriptors) {
    var stageId = sequence.nextValue();
    var deprecatedModuleFlows = mapItems(descriptors, md -> getModuleFlow(stageId, context, moduleType, null, md));
    return combineStages(stageId, deprecatedModuleFlows, moduleInstallerExecutor);
  }

  private static Map<String, Object> getModuleFlowParameters(ApplicationStageContext context, ModuleType moduleType,
    ModuleDescriptor descriptor, ModuleDescriptor installedDescriptor) {
    var flowParameters = new HashMap<String, Object>();

    flowParameters.put(PARAM_MODULE_ENTITLEMENT_TYPE, context.getEntitlementType());
    flowParameters.put(PARAM_MODULE_TYPE, moduleType);
    flowParameters.put(PARAM_APPLICATION_ID, context.getApplicationId());
    flowParameters.put(PARAM_APPLICATION_FLOW_ID, context.getCurrentFlowId());
    addFenceParameters(flowParameters, context);

    String moduleId = null;
    if (descriptor != null) {
      moduleId = descriptor.getId();
      flowParameters.put(PARAM_MODULE_DESCRIPTOR, descriptor);
    }

    if (installedDescriptor != null) {
      moduleId = Objects.toString(moduleId, installedDescriptor.getId());
      flowParameters.put(PARAM_INSTALLED_MODULE_DESCRIPTOR, installedDescriptor);
    }

    flowParameters.put(PARAM_MODULE_ID, moduleId);
    if (moduleType == MODULE) {
      var moduleDiscovery = context.getModuleDiscovery(moduleId);
      flowParameters.put(PARAM_MODULE_DISCOVERY, moduleDiscovery);
    }

    return flowParameters;
  }

  private static void addFenceParameters(Map<String, Object> parameters, ApplicationStageContext context) {
    if (context.getRootFlowId() != null) {
      parameters.put(PARAM_ROOT_FLOW_ID, context.getRootFlowId());
    }
    if (context.getFenceToken() != null) {
      parameters.put(PARAM_FENCE_TOKEN, context.getFenceToken());
    }
  }
}

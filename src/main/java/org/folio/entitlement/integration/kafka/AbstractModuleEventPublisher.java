package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.integration.kafka.model.ResourceEvent;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractModuleEventPublisher<T> extends ModuleDatabaseLoggingStage {

  private static final String EVENT_PUBLISHER_SUCCESS_STATUS = "eventPublisherSuccessStatus";

  protected KafkaEventPublisher kafkaEventPublisher;
  protected TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  private final boolean awaitCompletion;

  protected AbstractModuleEventPublisher(boolean awaitCompletion) {
    this.awaitCompletion = awaitCompletion;
  }

  @Override
  @SuppressWarnings("checkstyle:MethodLength")
  public void execute(ModuleStageContext ctx) {
    if (!guardFenceToken(ctx)) {
      return;
    }
    var tenant = ctx.getTenantName();
    var type = ctx.getModuleType();
    var moduleDesc = ctx.getModuleDescriptor();
    var installedModuleDesc = ctx.getInstalledModuleDescriptor();
    var applicationId = ctx.getApplicationId();
    var entitledApplicationId = ctx.getEntitledApplicationId();

    if (!isModuleUpdated(moduleDesc, installedModuleDesc)) {
      if (isModuleVersionChanged(moduleDesc, installedModuleDesc)) {
        getEventPayloadForNotChangedModule(applicationId, entitledApplicationId, type, moduleDesc, installedModuleDesc)
          .flatMap(payload -> createEvent(ctx.getStageId(), tenant, payload.getLeft(), payload.getRight()))
          .ifPresentOrElse(
            evt -> sendEvent(evt, ctx),
            () -> setSuccessStatus(FINISHED, ctx)
          );
      }

      return;
    }

    var newPayload = getEventPayload(applicationId, type, moduleDesc).orElse(null);
    var oldPayload = getEventPayload(entitledApplicationId, type, installedModuleDesc).orElse(null);

    createEvent(ctx.getStageId(), tenant, newPayload, oldPayload).ifPresentOrElse(
      evt -> sendEvent(evt, ctx),
      () -> setSuccessStatus(FINISHED, ctx));
  }

  @Autowired
  public void setKafkaEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  @Autowired
  public void setTenantEntitlementKafkaProperties(TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties) {
    this.tenantEntitlementKafkaProperties = tenantEntitlementKafkaProperties;
  }

  /**
   * Creates event payload from application id and module descriptor.
   *
   * @param applicationId - application identifier as {@link String}
   * @param descriptor - module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of created event payload, empty if event payload not provided
   */
  protected abstract Optional<T> getEventPayload(String applicationId, ModuleType type, ModuleDescriptor descriptor);

  /**
   * Creates topic name using tenant name.
   *
   * @param tenantName - tenant name as {@link String}
   * @return kafka topic name
   */
  protected abstract String getTopicNameByTenant(String tenantName);

  /**
   * Returns resource name for {@link ResourceEvent} object.
   *
   * @return resource name
   */
  protected abstract String getResourceName();

  /**
   * Provides a capability send event if module is not changed during upgrade process.
   *
   * @param applicationId - application identifier as {@link String}
   * @param entitledApplicationId - entitled application identifier as {@link String}
   * @param type - module type as {@link ModuleType} enum value
   * @param descriptor - new module descriptor as {@link ModuleDescriptor}
   * @param installedModuleDescriptor - installed module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of {@link T} event payload, empty if event payload not provided
   */
  protected Optional<Pair<T, T>> getEventPayloadForNotChangedModule(String applicationId, String entitledApplicationId,
    ModuleType type, ModuleDescriptor descriptor, ModuleDescriptor installedModuleDescriptor) {
    return Optional.empty();
  }

  /**
   * Resolves the status this stage should record on success.
   *
   * <p>Defaults to {@code FINISHED} rather than returning whatever is in the context. Two reasons: {@code execute}
   * has an early-return path (module descriptor unchanged and version unchanged) that sets nothing, and the flow
   * engine merges the context data of parallel sibling stages, so an unset key can read back a *different*
   * publisher's status rather than null. The attribute is keyed by stage id to close the second case at source.</p>
   */
  @Override
  protected EntityExecutionStatus getSuccessStatus(ModuleStageContext context) {
    return context.get(successStatusKey(context), FINISHED);
  }

  private static String successStatusKey(ModuleStageContext context) {
    return EVENT_PUBLISHER_SUCCESS_STATUS + "-" + context.getStageId();
  }

  /**
   * Creates {@link ResourceEvent} object for given tenant nane, new and old event bodies.
   *
   * @param eventId    - event id as {@link UUID}
   * @param tenantName - tenant name as {@link String}
   * @param newPayload - new value in {@link ResourceEvent}
   * @param oldPayload - old value in {@link ResourceEvent}
   * @return {@link Optional} of {@link ResourceEvent}, it will be empty if old and new values are not valid
   */
  private Optional<ResourceEvent<T>> createEvent(UUID eventId, String tenantName, T newPayload, T oldPayload) {
    return KafkaEventUtils.createEvent(eventId.toString(), getResourceName(), tenantName, newPayload, oldPayload);
  }

  private String getTopicName(String tenantName) {
    var topicTenant = tenantEntitlementKafkaProperties.isProducerTenantCollection()
      ? tenantEntitlementKafkaProperties.getTenantCollectionQualifier()
      : tenantName;
    return getTopicNameByTenant(topicTenant);
  }

  private void sendEvent(ResourceEvent<T> event, ModuleStageContext ctx) {
    var messageKey = ctx.getTenantName();
    var tenant = ctx.getTenantName();

    kafkaEventPublisher.send(getTopicName(tenant), messageKey, tenant, event);

    setSuccessStatus(awaitCompletion ? IN_PROGRESS : FINISHED, ctx);
  }

  private void setSuccessStatus(EntityExecutionStatus status, ModuleStageContext ctx) {
    ctx.put(successStatusKey(ctx), status);
  }
}

package org.folio.entitlement.integration.kafka;

import static java.lang.Boolean.TRUE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.integration.kafka.producer.KafkaUtils.getTenantTopicName;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.integration.kafka.producer.KafkaAdminService;
import org.folio.integration.kafka.producer.KafkaProducerProperties.KafkaTopic;
import org.folio.integration.kafka.producer.KafkaUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaTenantTopicCreator extends DatabaseLoggingStage<CommonStageContext> {

  private static final String PARAM_TOPICS_CREATED = "KafkaTenantTopicCreator.created";

  private final KafkaAdminService kafkaAdminService;
  private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  @Override
  public void execute(CommonStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    createTopicsIfRequired(context);
  }

  private void createTopicsIfRequired(CommonStageContext context) {
    if (isEntitleOrDesiredStateWithEntitle(context)) {
      var tenant = getTenant(context);

      var topicTenantValue = getTopicTenantValue(tenant);
      var createdTopics = createTopics(topicTenantValue);

      if (isNotEmpty(createdTopics)) {
        context.put(PARAM_TOPICS_CREATED, true);
        log.info("Tenant topics created: tenant = {}, topics = {}", topicTenantValue, createdTopics);
      } else {
        log.debug("No new tenant topics created: tenant = {}", topicTenantValue);
      }
    }
  }

  @Override
  public void cancel(CommonStageContext context) {
    if (!guardFenceToken(context)) {
      return;
    }
    var topicsCreated = context.<Boolean>get(PARAM_TOPICS_CREATED);
    if (!TRUE.equals(topicsCreated)) {
      return;
    }

    var tenant = getTenant(context);
    removeTenantTopics(tenant);
    log.info("Tenant topics removed: tenant = {}", tenant);
  }

  private List<String> createTopics(String topicTenantValue) {
    var tenantTopicsWithConfig = toStream(tenantEntitlementKafkaProperties.getTenantTopics())
      .collect(toMap(topic -> getTenantTopicName(topic.getName(), topicTenantValue), identity()));

    var existingTenantTopics = kafkaAdminService.findTopics(tenantTopicsWithConfig.keySet());
    log.debug("Existing tenant topics: topicNames = {}", existingTenantTopics);

    var createdTopics = new ArrayList<String>();
    tenantTopicsWithConfig.entrySet().stream()
      .filter(topic -> !existingTenantTopics.contains(topic.getKey()))
      .forEach(topic -> {
        createTopic(topic.getKey(), topic.getValue());
        createdTopics.add(topic.getKey());
      });

    return createdTopics;
  }

  private void createTopic(String topicName, KafkaTopic topicConfig) {
    var createdTopic = KafkaUtils.createTopic(topicName, topicConfig.getNumPartitions(),
      topicConfig.getReplicationFactor());
    kafkaAdminService.createTopic(createdTopic);
  }

  private void removeTenantTopics(String tenantName) {
    var tenantTopics = tenantEntitlementKafkaProperties.getTenantTopics();
    var topicsToPurge = mapItems(tenantTopics, topic -> getTenantTopicName(topic.getName(), tenantName));
    kafkaAdminService.deleteTopics(topicsToPurge);
  }

  private String getTopicTenantValue(String tenant) {
    return tenantEntitlementKafkaProperties.isProducerTenantCollection()
      ? tenantEntitlementKafkaProperties.getTenantCollectionQualifier() : tenant;
  }

  private static boolean isEntitleOrDesiredStateWithEntitle(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    return request.getType() == ENTITLE
      || request.getType() == STATE && !context.getApplicationStateTransitionPlan().entitleBucket().isEmpty();
  }

  private static String getTenant(CommonStageContext context) {
    return context.get(CommonStageContext.PARAM_TENANT_NAME);
  }
}

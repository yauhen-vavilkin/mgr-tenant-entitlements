package org.folio.entitlement.support;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.TOPIC_TENANT_COLLECTION_KEY;
import static org.folio.entitlement.support.TestUtils.httpClientWithDummySslContext;
import static org.folio.integration.kafka.producer.KafkaUtils.getEnvTopicName;
import static org.folio.integration.kafka.producer.KafkaUtils.getTenantTopicName;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.integration.folio.flow.FolioModuleEntitleFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleRevokeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleUpgradeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModulesFlowProvider;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.folio.stage.FolioModuleUninstaller;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCleaner;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceUpdater;
import org.folio.entitlement.integration.keycloak.KeycloakService;
import org.keycloak.admin.client.Keycloak;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final UUID TENANT_ID = UUID.fromString("6ad28dae-7c02-4f89-9320-153c55bf1914");
  public static final String TENANT_NAME = "test";
  public static final String TENANT_DESC = "test tenant";

  public static final String APPLICATION_NAME = "test-app";
  public static final String APPLICATION_VERSION = "1.0.0";
  public static final String APPLICATION_ID = APPLICATION_NAME + "-" + APPLICATION_VERSION;
  public static final String ENTITLED_APPLICATION_ID = APPLICATION_NAME + "-0.0.9";

  /**
   * Sample JWT that will expire in 2030 year for test_tenant with randomly generated user id.
   */
  public static final String OKAPI_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmb2xpbyIsInVzZXJfaWQiOiJlNmQyODVlOS03MmVkLT"
    + "QxYTQtOGIzYi01Y2VlNGNiYzg0MjUiLCJ0eXBlIjoiYWNjZXNzIiwiZXhwIjoxODkzNTAyODAwLCJpYXQiOjE3MjUzMDM2ODgsInRlbmFudCI6In"
    + "Rlc3RfdGVuYW50In0.SdtIQTrn7_XPnyi75Ai9bBkCWa8eQ69U6VAidCCRFFQ";

  public static final String TENANT_PARAMETERS = "loadSample=true";
  public static final Boolean PURGE = false;
  public static final Boolean IGNORE_ERRORS = false;

  public static final UUID FLOW_ID = UUID.randomUUID();
  public static final UUID APPLICATION_FLOW_ID = UUID.randomUUID();
  public static final UUID STAGE_UUID = UUID.randomUUID();
  public static final UUID OWNER_INSTANCE_ID = UUID.randomUUID();
  public static final String FLOW_STAGE_ID = FLOW_ID + "/ApplicationsFlow/Level-0/" + APPLICATION_FLOW_ID;

  public static final HttpClient HTTP_CLIENT_DUMMY_SSL = httpClientWithDummySslContext();

  public static final List<Class<?>> COMMON_KEYCLOAK_INTEGRATION_BEAN_TYPES =
    List.of(Keycloak.class, KeycloakService.class);

  public static final List<Class<?>> FOLIO_KEYCLOAK_INTEGRATION_BEAN_TYPES = List.of(
    KeycloakModuleResourceCreator.class, KeycloakModuleResourceUpdater.class, KeycloakModuleResourceCleaner.class);

  public static final List<Class<?>> FOLIO_MODULE_INSTALLER_BEAN_TYPES = List.of(
    FolioModulesFlowProvider.class, FolioModuleEntitleFlowFactory.class, FolioModuleUpgradeFlowFactory.class,
    FolioModuleRevokeFlowFactory.class, FolioModuleInstaller.class, FolioModuleUninstaller.class,
    FolioModuleEventPublisher.class);

  public static String entitlementTopic() {
    return getEnvTopicName("entitlement");
  }

  public static String capabilitiesTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.capability", TENANT_NAME);
  }

  public static String capabilitiesTenantCollectionTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.capability", TOPIC_TENANT_COLLECTION_KEY);
  }

  public static String scheduledJobsTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.scheduled-job", TENANT_NAME);
  }

  public static String scheduledJobsTenantCollectionTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.scheduled-job", TOPIC_TENANT_COLLECTION_KEY);
  }

  public static String systemUserTenantTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.system-user", TENANT_NAME);
  }

  public static String systemUserTenantCollectionTopic() {
    return getTenantTopicName("mgr-tenant-entitlements.system-user", TOPIC_TENANT_COLLECTION_KEY);
  }
}

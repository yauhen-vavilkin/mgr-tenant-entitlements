package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.folio.entitlement.repository.InstanceHeartbeatRepository;
import org.folio.entitlement.service.InstanceContext;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false"
})
class InstanceHeartbeatRepositoryIT extends BaseIntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private InstanceContext instanceContext;
  @Autowired private InstanceHeartbeatRepository repository;

  @Test
  @Transactional
  void upsertHeartbeat_recreatesDeletedInstanceAndPreservesStartedAt() {
    var instanceId = instanceContext.getInstanceId();
    var startedAt = instanceContext.getStartedAt();
    repository.deleteById(instanceId);
    entityManager.flush();

    var freshHeartbeat = startedAt.plusSeconds(1);
    repository.upsertHeartbeat(instanceId, startedAt, freshHeartbeat);
    entityManager.flush();
    entityManager.clear();

    var instance = repository.findById(instanceId).orElseThrow();
    assertThat(instance.getStartedAt()).isEqualTo(startedAt);
    assertThat(instance.getLastHeartbeat()).isEqualTo(freshHeartbeat);
  }
}

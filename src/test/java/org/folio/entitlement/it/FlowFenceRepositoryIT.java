package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.INTERRUPTED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityApplicationFlowEntitlementType;
import org.folio.entitlement.domain.entity.type.EntityFlowEntitlementType;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.FlowRecoveryService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false"
})
class FlowFenceRepositoryIT extends BaseIntegrationTest {

  private static final UUID FLOW_ID = UUID.randomUUID();

  @Autowired private FlowRepository flowRepository;
  @Autowired private ApplicationFlowRepository applicationFlowRepository;
  @Autowired private FlowRecoveryService flowRecoveryService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void concurrentReclaimers_onlyOneConditionalUpdateWins() throws Exception {
    createFlow();
    var attempts = runConcurrentReclaims();

    assertThat(attempts).containsExactly(true, true);
    var flow = readFlow();
    assertThat(flow.getStatus()).isEqualTo(INTERRUPTED);
    assertThat(flow.getFenceToken()).isEqualTo(1L);
    assertThat(readApplicationFlow().getStatus()).isEqualTo(INTERRUPTED);
  }

  private void createFlow() {
    inTransaction(() -> {
      var flow = new FlowEntity();
      flow.setId(FLOW_ID);
      flow.setStatus(IN_PROGRESS);
      flow.setType(EntityFlowEntitlementType.ENTITLE);
      flow.setFenceToken(0L);
      flow.setOwnerInstanceId(UUID.randomUUID());
      flowRepository.saveAndFlush(flow);

      var applicationFlow = new ApplicationFlowEntity();
      applicationFlow.setId(UUID.randomUUID());
      applicationFlow.setFlowId(FLOW_ID);
      applicationFlow.setApplicationId("test-app-1.0.0");
      applicationFlow.setApplicationName("test-app");
      applicationFlow.setApplicationVersion("1.0.0");
      applicationFlow.setStatus(org.folio.entitlement.domain.entity.type.EntityExecutionStatus.QUEUED);
      applicationFlow.setType(EntityApplicationFlowEntitlementType.ENTITLE);
      applicationFlowRepository.saveAndFlush(applicationFlow);
    });
  }

  private List<Boolean> runConcurrentReclaims() throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(this::reclaim);
      var second = executor.submit(this::reclaim);
      return List.of(first.get(), second.get());
    }
  }

  private boolean reclaim() {
    return flowRecoveryService.recover(List.of(new ApplicationFlow()
      .flowId(FLOW_ID).status(ExecutionStatus.QUEUED)));
  }

  private FlowEntity readFlow() {
    return transaction(() -> flowRepository.findById(FLOW_ID).orElseThrow());
  }

  private ApplicationFlowEntity readApplicationFlow() {
    return transaction(() -> applicationFlowRepository.findByFlowId(FLOW_ID).get(0));
  }

  private void inTransaction(Runnable action) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
  }

  private <T> T transaction(java.util.function.Supplier<T> action) {
    return new TransactionTemplate(transactionManager).execute(status -> action.get());
  }
}

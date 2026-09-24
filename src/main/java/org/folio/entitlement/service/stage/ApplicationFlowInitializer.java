package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicationFlowInitializer extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationFlowRepository applicationFlowRepository;

  /**
   * Starts the application flow with a compare-and-set: a queued flow that was already failed by
   * {@code FlowService#failIfNotTerminal} - or whose parent flow was - must not be brought back to life, so the
   * update refuses and this stage throws, stopping the background execution at this application boundary. The
   * parent flow check also covers rows queued after the timeout, which the bulk fail-update could not see.
   *
   * <p>{@code finishedAt} is set to mirror the {@code @UpdateTimestamp} touch of the previous entity save:
   * {@code findLastFlows} resolves the latest application flow by {@code MAX(finished_at)} and must see
   * in-progress flows.</p>
   */
  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    var applicationFlowId = context.getCurrentFlowId();
    var fenceToken = context.getFenceToken();
    var updated = fenceToken == null
      ? applicationFlowRepository.updateStatusIfCurrentInAndFlowActive(
        applicationFlowId, IN_PROGRESS, NON_TERMINAL_STATUSES, ZonedDateTime.now(ZoneId.systemDefault()))
      : applicationFlowRepository.updateStatusIfCurrentInAndFlowActiveAndFenceToken(
        applicationFlowId, IN_PROGRESS, NON_TERMINAL_STATUSES, ZonedDateTime.now(ZoneId.systemDefault()), fenceToken);

    if (updated == 0) {
      throw new IllegalStateException(String.format(
        "Application flow cannot be started, because it or its flow is already in a terminal status "
          + "[applicationFlowId: %s]", applicationFlowId));
    }
  }
}

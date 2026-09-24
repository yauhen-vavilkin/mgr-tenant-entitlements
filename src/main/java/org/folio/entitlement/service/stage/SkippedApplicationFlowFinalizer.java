package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SkippedApplicationFlowFinalizer extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationFlowRepository applicationFlowRepository;

  /**
   * The status check is a part of the delete statement: a row force-failed by the execution timeout between a
   * read and a delete must not be removed - it was already reported to the caller as failed.
   */
  @Override
  @Transactional
  public void execute(ApplicationStageContext stageContext) {
    if (!guardFenceToken(stageContext)) {
      return;
    }
    applicationFlowRepository.removeQueuedFlow(stageContext.getCurrentFlowId());
  }
}

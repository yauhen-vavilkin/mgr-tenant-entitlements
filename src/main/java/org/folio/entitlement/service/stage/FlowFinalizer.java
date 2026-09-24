package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.springframework.transaction.annotation.Transactional;

public abstract class FlowFinalizer extends AbstractFlowFinalizer<FlowEntity, CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   */
  protected FlowFinalizer(FlowRepository flowRepository, FlowFinalizerStatusProvider<CommonStageContext> statusProvider,
    ApplicationFlowService applicationFlowService) {
    super(flowRepository, statusProvider);
    this.applicationFlowService = applicationFlowService;
  }

  /**
   * Removes queued application flows even when the status write was skipped: after an execution timeout, queued
   * rows can still be created by the running flow and must not survive the finalizer - a leftover QUEUED row wins
   * {@code findLastFlows}' latest-flow resolution and blocks any future request for its application.
   */
  @Override
  @Transactional
  public void execute(CommonStageContext context) {
    super.execute(context);
    if (context.getFenceToken() != null && !context.isFenceWriteSucceeded()) {
      return;
    }
    if (!guardFenceToken(context)) {
      return;
    }
    applicationFlowService.removeAllQueuedFlows(context.getCurrentFlowId());
  }
}

package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.repository.AbstractFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.FlowCompletionService;
import org.folio.entitlement.utils.TransactionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractFlowFinalizer<T extends AbstractFlowEntity, C extends IdentifiableStageContext>
  extends DatabaseLoggingStage<C> {

  // Setter-injected rather than constructor-injected: every concrete finalizer calls super(...) explicitly, and
  // threading one more argument through ten subclasses buys nothing. Matches how DatabaseLoggingStage takes its
  // own collaborators.
  private FlowCompletionService flowCompletionService;
  private TransactionHelper transactionHelper;

  private final AbstractFlowRepository<T> abstractFlowRepository;
  private final FlowFinalizerStatusProvider<C> statusProvider;
  private FlowRepository rootFlowRepository;

  /**
   * Sets the final flow status with a single compare-and-set statement and runs {@link #afterFlowStatusUpdate(C)}
   * only when the status was actually written.
   *
   * <p>A timed-out flow is failed by {@code FlowService#failIfNotTerminal}, but the flow engine cannot abort it, so
   * it keeps running and eventually reaches this stage. The compare-and-set keeps the already reported status (and
   * skips the finalizer's side effects) - a plain read-check-save would race with the timeout update and could
   * overwrite it.</p>
   *
   * <p>When the status provider returns {@code IN_PROGRESS}, async stage confirmations are still pending. The flow
   * row keeps its status and instead records an {@code awaitingAsyncSince} anchor: until that anchor exists no
   * inbound result may complete the flow, which is what stops a fast downstream response from finishing a flow
   * whose remaining stages have not started yet. {@link #afterFlowStatusUpdate(C)} is still invoked so that
   * application finalizers persist entitlement/revoke/upgrade records independently of async completion.</p>
   */
  @Override
  @Transactional
  public void execute(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var status = EntityExecutionStatus.from(statusProvider.getFinalStatus(context));

    if (status == IN_PROGRESS) {
      var anchored = markAwaitingAsync(context, entitlementFlowId);

      if (anchored > 0) {
        advanceFenceToken(context);

        log.info("Flow is waiting for async stage confirmations [flowId: {}]", entitlementFlowId);
      }
    } else {
      var updated = updateStatus(context, entitlementFlowId, status, ZonedDateTime.now(ZoneId.systemDefault()));

      if (updated == 0) {
        log.warn("Flow status update to {} is skipped, flow is already in a terminal status [flowId: {}]",
          status, entitlementFlowId);
        return;
      }
      advanceFenceToken(context);
    }

    // Called for both terminal and IN_PROGRESS: application finalizers must persist entitlement/revoke/upgrade
    // records regardless of whether async stage confirmations have arrived yet.
    afterFlowStatusUpdate(context);
  }

  /**
   * Re-attempts flow completion once this finalizer's own stage row is terminal.
   *
   * <p>This is the second half of the completion contract, and it is needed in both directions. A result event
   * that arrived while {@link #execute(C)} was running was blocked by this stage's own {@code IN_PROGRESS} row and
   * will not retry on its own; conversely {@code execute} may have observed an async stage that has since been
   * confirmed. Without a re-check both parties can see the other as pending and the flow is stranded.</p>
   *
   * <p>The work must happen after commit: inside the transaction this stage's row still reads {@code IN_PROGRESS},
   * so the completion predicate would fail for the very reason we are retrying. The call is unconditional and
   * stateless - completion is a guarded compare-and-set that requires {@code IN_PROGRESS} and a non-null anchor,
   * so it is a no-op for flows this finalizer has just moved to a terminal status.</p>
   */
  @Override
  @Transactional
  public void onSuccess(C context) {
    super.onSuccess(context);

    var entitlementFlowId = context.getCurrentFlowId();
    var time = ZonedDateTime.now(ZoneId.systemDefault());

    transactionHelper.executeAfterCommitInNewTrx(status -> flowCompletionService.completeIfNoActiveStages(
      entitlementFlowId, time));
  }

  @Autowired
  public void setRootFlowRepository(FlowRepository rootFlowRepository) {
    this.rootFlowRepository = rootFlowRepository;
  }

  @Autowired
  public void setFlowCompletionService(FlowCompletionService flowCompletionService) {
    this.flowCompletionService = flowCompletionService;
  }

  @Autowired
  public void setTransactionHelper(TransactionHelper transactionHelper) {
    this.transactionHelper = transactionHelper;
  }

  protected void afterFlowStatusUpdate(C context) {}

  private void advanceFenceToken(C context) {
    if (isRootFlow(context) && context.get(CommonStageContext.PARAM_FENCE_TOKEN) != null) {
      var token = context.<Long>get(CommonStageContext.PARAM_FENCE_TOKEN);
      context.put(CommonStageContext.PARAM_FENCE_TOKEN, token + 1);
    }
  }

  private boolean isRootFlow(C context) {
    return context instanceof CommonStageContext;
  }

  private int markAwaitingAsync(C context, java.util.UUID flowId) {
    if (!isRootFlow(context) || rootFlowRepository == null
        || context.get(CommonStageContext.PARAM_FENCE_TOKEN) == null) {
      return abstractFlowRepository.markAwaitingAsync(flowId, ZonedDateTime.now(ZoneId.systemDefault()));
    }
    return rootFlowRepository.markAwaitingAsyncIfFenceMatches(flowId,
      ZonedDateTime.now(ZoneId.systemDefault()), context.get(CommonStageContext.PARAM_FENCE_TOKEN));
  }

  private int updateStatus(C context, java.util.UUID flowId, EntityExecutionStatus status, ZonedDateTime finishedAt) {
    if (!isRootFlow(context) || rootFlowRepository == null
        || context.get(CommonStageContext.PARAM_FENCE_TOKEN) == null) {
      return abstractFlowRepository.updateStatusIfCurrentIn(flowId, status,
        allowedCurrentStatuses(status), finishedAt);
    }
    return rootFlowRepository.updateStatusIfFenceMatches(flowId, status, allowedCurrentStatuses(status), finishedAt,
      context.get(CommonStageContext.PARAM_FENCE_TOKEN));
  }

  /**
   * Cancellation must be able to roll back a FINISHED flow and to supersede a timeout-forced FAILED status;
   * FINISHED/FAILED must not overwrite a status already reported to the caller.
   */
  private static Set<EntityExecutionStatus> allowedCurrentStatuses(EntityExecutionStatus target) {
    return target == CANCELLED || target == CANCELLATION_FAILED
      ? EnumSet.allOf(EntityExecutionStatus.class)
      : NON_TERMINAL_STATUSES;
  }
}

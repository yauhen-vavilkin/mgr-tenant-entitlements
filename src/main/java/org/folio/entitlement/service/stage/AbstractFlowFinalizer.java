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
  private FlowRepository rootFlowRepository;

  private final AbstractFlowRepository<T> abstractFlowRepository;
  private final FlowFinalizerStatusProvider<C> statusProvider;

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
    if (context.getRootFlowId() != null && context.getFenceToken() != null && !guardFenceToken(context)) {
      log.warn("Flow finalizer skipped after ownership was reclaimed [flowId: {}, observedFenceToken: {}]",
        context.getCurrentFlowId(), context.getFenceToken());
      context.withFenceWriteSucceeded(false);
      return;
    }
    var status = EntityExecutionStatus.from(statusProvider.getFinalStatus(context));
    var updated = status == IN_PROGRESS ? markAsyncAnchor(context) : updateTerminalStatus(context, status);
    if (!updated) {
      return;
    }
    // Application finalizers persist side effects only after their owner write was fenced successfully.
    afterFlowStatusUpdate(context);
  }

  private boolean markAsyncAnchor(C context) {
    var flowId = context.getCurrentFlowId();
    var anchored = markAwaitingAsync(context, flowId);
    if (anchored == 0 && context.getFenceToken() != null && !isFenceTokenCurrent(context)) {
      log.warn("Flow finalizer lost the ownership fence race [flowId: {}, observedFenceToken: {}]",
        flowId, context.getFenceToken());
      context.withFenceWriteSucceeded(false);
      return false;
    }
    if (anchored > 0) {
      incrementRootFence(context, flowId);
      log.info("Flow is waiting for async stage confirmations [flowId: {}]", flowId);
    }
    // An already-present anchor is a successful idempotent retry when the observed fence is still current.
    context.withFenceWriteSucceeded(true);
    return true;
  }

  private boolean updateTerminalStatus(C context, EntityExecutionStatus status) {
    var flowId = context.getCurrentFlowId();
    if (updateStatus(context, flowId, status) == 0) {
      log.warn("Flow status update to {} is skipped, flow is already in a terminal status [flowId: {}]",
        status, flowId);
      context.withFenceWriteSucceeded(false);
      return false;
    }
    incrementRootFence(context, flowId);
    context.withFenceWriteSucceeded(true);
    return true;
  }

  private static void incrementRootFence(IdentifiableStageContext context, java.util.UUID flowId) {
    if (context.getRootFlowId() != null && context.getRootFlowId().equals(flowId)
        && context.getFenceToken() != null) {
      context.withFenceToken(context.getFenceToken() + 1);
    }
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
  public void setFlowCompletionService(FlowCompletionService flowCompletionService) {
    this.flowCompletionService = flowCompletionService;
  }

  @Autowired
  public void setTransactionHelper(TransactionHelper transactionHelper) {
    this.transactionHelper = transactionHelper;
  }

  @Autowired
  public void setRootFlowRepository(FlowRepository rootFlowRepository) {
    this.rootFlowRepository = rootFlowRepository;
  }

  protected void afterFlowStatusUpdate(C context) {}

  protected boolean isFenceTokenCurrent(C context) {
    var fenceToken = context.getFenceToken();
    if (fenceToken == null || context.getRootFlowId() == null) {
      return true;
    }
    return rootFlowRepository.findById(context.getRootFlowId())
      .map(flow -> fenceToken.equals(flow.getFenceToken())).orElse(false);
  }

  private int markAwaitingAsync(C context, java.util.UUID flowId) {
    var fenceToken = context.getFenceToken();
    var timestamp = ZonedDateTime.now(ZoneId.systemDefault());
    if (fenceToken != null) {
      if (abstractFlowRepository instanceof org.folio.entitlement.repository.FlowRepository flowRepository) {
        return flowRepository.markAwaitingAsyncWithFenceToken(flowId, timestamp, fenceToken);
      }
      return ((org.folio.entitlement.repository.ApplicationFlowRepository) abstractFlowRepository)
        .markAwaitingAsyncWithFenceToken(flowId, timestamp, fenceToken);
    }
    return abstractFlowRepository.markAwaitingAsync(flowId, timestamp);
  }

  private int updateStatus(C context, java.util.UUID flowId, EntityExecutionStatus status) {
    var fenceToken = context.getFenceToken();
    if (fenceToken != null) {
      var statuses = allowedCurrentStatuses(status);
      var timestamp = ZonedDateTime.now(ZoneId.systemDefault());
      if (abstractFlowRepository instanceof org.folio.entitlement.repository.FlowRepository flowRepository) {
        return flowRepository.updateStatusIfCurrentInAndFenceToken(flowId, status, statuses, timestamp, fenceToken);
      }
      return ((org.folio.entitlement.repository.ApplicationFlowRepository) abstractFlowRepository)
        .updateStatusIfCurrentInAndFenceToken(flowId, status, statuses, timestamp, fenceToken);
    }
    return abstractFlowRepository.updateStatusIfCurrentIn(
      flowId, status, allowedCurrentStatuses(status), ZonedDateTime.now(ZoneId.systemDefault()));
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

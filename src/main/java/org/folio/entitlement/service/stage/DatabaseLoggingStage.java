package org.folio.entitlement.service.stage;

import static java.lang.String.format;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.ModuleStageContext.ATTR_RETRY_INFO;
import static org.folio.entitlement.utils.EntitlementServiceUtils.getErrorMessage;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.domain.model.RetryInformation;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.flow.api.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public abstract class DatabaseLoggingStage<C extends IdentifiableStageContext> implements Stage<C> {

  protected FlowStageRepository stageRepository;
  protected FlowRepository flowRepository;
  protected ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @Override
  @Transactional
  public void onStart(C context) {
    if (!guardFenceToken(context)) {
      throw new IllegalStateException(String.format(
        "Flow stage cannot start after ownership was reclaimed [flowId: %s, stageId: %s]",
        context.getCurrentFlowId(), context.getStageId()));
    }
    var entity = new FlowStageEntity();
    var stageId = UUID.randomUUID();

    entity.setId(stageId);
    entity.setFlowId(context.getCurrentFlowId());
    entity.setStageName(getStageName(context));
    entity.setStatus(IN_PROGRESS);

    stageRepository.save(entity);

    context.withStageId(stageId);
  }

  /**
   * Records the stage outcome, unless the stage is deliberately being left pending.
   *
   * <p>A publisher configured to await async confirmation returns {@code IN_PROGRESS} here. {@link #onStart(C)}
   * already persisted that status, so re-asserting it carries no information - and it is actively harmful: a
   * downstream result that resolved this stage between {@code execute} and this callback would be overwritten and
   * the stage pinned at {@code IN_PROGRESS} for good, since the result has already been consumed.</p>
   */
  @Override
  @Transactional
  public void onSuccess(C context) {
    var status = getSuccessStatus(context);

    if (status == IN_PROGRESS) {
      log.debug("Flow stage {} is left 'In Progress' pending async confirmation [stageId: {}]",
        getStageName(context), context.getStageId());
      return;
    }

    setEntitlementStageStatus(context, status, null);
  }

  @Override
  @Transactional
  public void onCancel(C context) {
    setEntitlementStageStatus(context, CANCELLED, null);
  }

  @Override
  @Transactional
  public void onCancelError(C context, Exception exception) {
    setEntitlementStageStatus(context, CANCELLATION_FAILED, exception);
  }

  @Override
  @Transactional
  public void onError(C context, Exception exception) {
    setEntitlementStageStatus(context, FAILED, exception);
  }

  @Autowired
  public void setStageRepository(FlowStageRepository flowStageRepository) {
    this.stageRepository = flowStageRepository;
  }

  @Autowired
  public void setFlowRepository(FlowRepository flowRepository) {
    this.flowRepository = flowRepository;
  }

  protected boolean isFenceTokenCurrent(C context) {
    var fenceToken = context.getFenceToken();
    return fenceToken == null || context.getRootFlowId() == null
      || flowRepository.findById(context.getRootFlowId())
      .map(flow -> fenceToken.equals(flow.getFenceToken())).orElse(false);
  }

  /**
   * Checks and locks the root fence for the duration of the current transaction. This orders an owner side effect
   * against reclamation: once recovery has advanced the token, this update affects no rows and the caller must stop.
   */
  protected boolean guardFenceToken(C context) {
    var fenceToken = context.getFenceToken();
    return fenceToken == null || context.getRootFlowId() == null
      || flowRepository.guardFenceToken(context.getRootFlowId(), fenceToken) > 0;
  }

  @Autowired
  public void setThreadLocalModuleStageContext(ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    this.threadLocalModuleStageContext = threadLocalModuleStageContext;
  }

  @Override
  public String getId() {
    return this.getClass().getSimpleName();
  }

  /**
   * Returns stage name based on {@link C} context object.
   *
   * @param context - stage context
   * @return stage name based on stage context
   */
  @SuppressWarnings("unused")
  public String getStageName(C context) {
    return getId();
  }

  protected EntityExecutionStatus getSuccessStatus(C context) {
    return FINISHED;
  }

  private void setEntitlementStageStatus(C context, EntityExecutionStatus status, Exception error) {
    if (!guardFenceToken(context)) {
      log.warn("Flow stage write skipped after ownership was reclaimed [flowId: {}, stageId: {}, "
        + "observedFenceToken: {}]", context.getCurrentFlowId(), context.getStageId(), context.getFenceToken());
      return;
    }
    var stageExecutionKey = FlowStageKey.of(context.getCurrentFlowId(), getStageName(context));
    var stageExecutionEntity = stageRepository.getReferenceById(stageExecutionKey);
    stageExecutionEntity.setStatus(status);

    if (error != null) {
      stageExecutionEntity.setErrorType(error.getClass().getSimpleName());
      stageExecutionEntity.setErrorMessage(getErrorMessage(error));

      log.error(format("Flow stage %s %s execution error", context.getStageId(), getStageName(context)), error);
    }

    var retryInfo = (RetryInformation) context.get(ATTR_RETRY_INFO);
    if (retryInfo != null) {
      stageExecutionEntity.setRetriesCount(retryInfo.getRetriesCount());
      stageExecutionEntity.setRetriesInfo(String.join("\n\n", retryInfo.getErrors()));
      context.put(ATTR_RETRY_INFO, null);
    }
    threadLocalModuleStageContext.clear();

    stageRepository.save(stageExecutionEntity);
  }
}

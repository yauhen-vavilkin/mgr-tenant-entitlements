package org.folio.entitlement.domain.model;

import java.util.UUID;
import org.folio.flow.api.AbstractStageContextWrapper;
import org.folio.flow.api.StageContext;

public class IdentifiableStageContext extends AbstractStageContextWrapper {

  public static final String PARAM_STAGE_ID = "stageId";
  public static final String PARAM_FENCE_TOKEN = "fenceToken";
  public static final String PARAM_ROOT_FLOW_ID = "rootFlowId";
  public static final String PARAM_FENCE_WRITE_SUCCEEDED = "fenceWriteSucceeded";

  /**
   * Creates {@link IdentifiableStageContext} wrapper from {@link StageContext}.
   *
   * @param stageContext - stage context
   */
  protected IdentifiableStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Returns current flow identifier.
   *
   * <p>
   * For global flow it returns root flow id, for application flow it should return application flow id.
   * </p>
   *
   * @return flow identifier as {@link UUID} object
   */
  public UUID getCurrentFlowId() {
    return UUID.fromString(context.flowId());
  }

  public UUID getStageId() {
    return context.get(PARAM_STAGE_ID);
  }

  public Long getFenceToken() {
    Long token = context.get(PARAM_FENCE_TOKEN);
    return token == null ? context.<Long>getFlowParameter(PARAM_FENCE_TOKEN) : token;
  }

  public UUID getRootFlowId() {
    UUID rootFlowId = context.getFlowParameter(PARAM_ROOT_FLOW_ID);
    if (rootFlowId != null) {
      return rootFlowId;
    }
    try {
      return UUID.fromString(context.flowId());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  public IdentifiableStageContext withFenceToken(Long fenceToken) {
    context.put(PARAM_FENCE_TOKEN, fenceToken);
    return this;
  }

  public IdentifiableStageContext withFenceWriteSucceeded(boolean succeeded) {
    context.put(PARAM_FENCE_WRITE_SUCCEEDED, succeeded);
    return this;
  }

  public boolean isFenceWriteSucceeded() {
    return Boolean.TRUE.equals(context.get(PARAM_FENCE_WRITE_SUCCEEDED));
  }

  public IdentifiableStageContext withStageId(UUID stageId) {
    context.put(PARAM_STAGE_ID, stageId);
    return this;
  }
}

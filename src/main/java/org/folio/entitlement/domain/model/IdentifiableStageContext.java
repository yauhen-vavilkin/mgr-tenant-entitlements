package org.folio.entitlement.domain.model;

import java.util.UUID;
import org.folio.flow.api.AbstractStageContextWrapper;
import org.folio.flow.api.StageContext;

public class IdentifiableStageContext extends AbstractStageContextWrapper {

  public static final String PARAM_STAGE_ID = "stageId";
  public static final String PARAM_FENCE_TOKEN = "fenceToken";

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
    return context.get(PARAM_FENCE_TOKEN);
  }

  public IdentifiableStageContext withFenceToken(Long fenceToken) {
    context.put(PARAM_FENCE_TOKEN, fenceToken);
    return this;
  }

  public IdentifiableStageContext withStageId(UUID stageId) {
    context.put(PARAM_STAGE_ID, stageId);
    return this;
  }
}

package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.FlowMapper;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.InstanceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class FlowService {

  private final FlowMapper flowMapper;
  private final FlowRepository flowRepository;
  private final FlowStageService flowStageService;
  private final ApplicationFlowService applicationFlowService;
  private final InstanceContext instanceContext;

  /**
   * Retrieves {@link ApplicationFlow} by query and pagination parameters (limit, offset).
   *
   * @param query - CQL query to search by entitlement flows
   * @param limit - a limit for the number of elements returned in the response
   * @param offset - a number of elements to skip
   * @return {@link SearchResult} object with found {@link ApplicationFlow} values
   */
  @Transactional(readOnly = true)
  public SearchResult<Flow> find(String query, Integer limit, Integer offset) {
    var pageable = OffsetRequest.of(offset, limit);
    var foundEntities = isNotBlank(query)
      ? flowRepository.findByCql(query, pageable)
      : flowRepository.findAll(pageable);

    var flowIds = mapItems(foundEntities.getContent(), FlowEntity::getId);
    var applicationFlowIdsMap = applicationFlowService.findByFlowIds(flowIds);

    var flows = foundEntities.stream()
      .map(flowMapper::map)
      .map(e -> e.applicationFlows(applicationFlowIdsMap.getOrDefault(e.getId(), emptyList())))
      .toList();

    return SearchResult.of((int) foundEntities.getTotalElements(), flows);
  }

  /**
   * Retrieves entitlement flow by its identifier.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return found {@link org.folio.entitlement.domain.dto.Flow} object
   * @throws jakarta.persistence.EntityNotFoundException if flow is not found by id
   */
  @Transactional(readOnly = true)
  public Flow getById(UUID flowId, boolean includeStages) {
    var flowEntity = flowRepository.getReferenceById(flowId);
    var flow = flowMapper.map(flowEntity);
    var applicationFlows = applicationFlowService.findByFlowId(flowId, includeStages);

    if (includeStages) {
      flow.stages(flowStageService.findByFlowId(flowId).getRecords());
    }

    return flow.applicationFlows(applicationFlows);
  }

  /**
   * Creates flow entity in database.
   *
   * <p>An already existing row is expected to be the FAILED one inserted by {@link #createFailed} for a flow that
   * timed out before it was scheduled - such a flow must not be started, so this method throws instead of
   * overwriting, reporting the actual status of the existing row.</p>
   *
   * @param flow - flow representation
   * @return created {@link Flow} entity
   * @throws IllegalStateException if the flow row already exists
   */
  public Flow create(Flow flow) {
    var flowId = requireNonNull(flow.getId());
    var existingStatus = flowRepository.findStatusById(flowId);
    if (existingStatus.isPresent()) {
      throw new IllegalStateException(String.format(
        "Flow cannot be started, because it has already been created with %s status [flowId: %s]",
        existingStatus.get(), flowId));
    }

    var flowEntity = flowMapper.map(flow);
    var savedEntity = flowRepository.save(flowEntity);
    return flowMapper.map(savedEntity);
  }

  /**
   * Records a flow that timed out before its initializer stage created the flow row: a FAILED row is inserted, so
   * {@link #create(Flow)} refuses to start the flow when the engine eventually schedules it. The timestamps are
   * populated by the entity's creation/update callbacks on insert.
   *
   * <p>{@code saveAndFlush} surfaces a concurrent insert by the initializer as a data-access exception inside this
   * method - the caller falls back to {@link #failIfNotTerminal(UUID)} in a new transaction.</p>
   */
  public void createFailed(UUID flowId, EntitlementRequest request) {
    var flow = new Flow()
      .id(flowId)
      .tenantId(request.getTenantId())
      .status(ExecutionStatus.FAILED)
      .type(request.getType())
      .ownerInstanceId(instanceContext.getInstanceId())
      .fenceToken(0L);

    flowRepository.saveAndFlush(flowMapper.map(flow));
    log.warn("Flow timed out before it was started, created as failed [flowId: {}]", flowId);
  }

  /**
   * Marks the flow, its application flows and their in-progress stages as failed, if the flow has not reached a
   * terminal status yet.
   *
   * <p>Used when waiting for a synchronously executed flow timed out. The flow itself is not stopped - the flow engine
   * provides no way to abort a running flow - so the update is conditional and the flow finalizer stages keep the
   * status written here.</p>
   *
   * <p>The flow row is updated first: the flow finalizer is the last stage of a flow, so a terminal flow status
   * implies all application flow and stage rows are terminal as well, and once the flow row is failed a concurrently
   * finishing flow finalizer can no longer win against the application flow updates below.</p>
   *
   * @return {@code true} when the flow was marked as failed, {@code false} when it already was in a terminal status
   */
  public boolean failIfNotTerminal(UUID flowId) {
    var finishedAt = ZonedDateTime.now(ZoneId.systemDefault());
    var updatedFlows = flowRepository.updateStatusIfCurrentIn(flowId, FAILED, NON_TERMINAL_STATUSES, finishedAt);
    if (updatedFlows == 0) {
      log.warn("Flow is not marked as failed, it has already reached a terminal status or has not been created yet "
        + "[flowId: {}]", flowId);
      return false;
    }

    var updatedApplicationFlows = applicationFlowService.failNonTerminalFlows(flowId, finishedAt);
    var updatedStages = flowStageService.failNonTerminalStages(flowId, finishedAt);

    log.warn("Flow is forcibly marked as failed [flowId: {}, applicationFlows: {}, stages: {}]",
      flowId, updatedApplicationFlows, updatedStages);

    return true;
  }

  /**
   * Retrieves the current status of the flow, empty when the flow row does not exist yet.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @return flow status as {@link ExecutionStatus}, or empty
   */
  @Transactional(readOnly = true)
  public Optional<ExecutionStatus> findStatus(UUID flowId) {
    return flowRepository.findStatusById(flowId).map(status -> ExecutionStatus.valueOf(status.name()));
  }

  @Transactional(readOnly = true)
  public Flow getTopLevelFlow(UUID flowId) {
    var topLevelFlow = applicationFlowService.findById(flowId)
      .map(appFlow -> flowRepository.getReferenceById(appFlow.getFlowId()))
      .orElseGet(() -> flowRepository.getReferenceById(flowId));

    return flowMapper.map(topLevelFlow);
  }

  public int finishFlowIfNoActiveStages(UUID flowId, ZonedDateTime finishedAt) {
    return flowRepository.updateStatusByIdIfCurrentInAndNoStagesWithStatus(flowId, FINISHED,
      Set.of(IN_PROGRESS), finishedAt);
  }

  public int finishFlowIfNoActiveStages(UUID flowId, ZonedDateTime finishedAt, Long fenceToken) {
    return flowRepository.updateStatusByIdIfCurrentInAndNoStagesWithStatusAndFence(flowId, FINISHED,
      Set.of(IN_PROGRESS), finishedAt, fenceToken);
  }

  public int failActiveFlow(UUID id, ZonedDateTime finishedAt) {
    return flowRepository.updateStatusIfCurrentIn(id, FAILED, Set.of(IN_PROGRESS), finishedAt);
  }

  /**
   * Reclaims a flow after its owner is presumed lost. The observed fence token is mandatory: a stale reclaim cannot
   * replace a result written by another instance.
   */
  public boolean reclaim(UUID flowId, Long observedFenceToken) {
    var updated = flowRepository.reclaimIfFenceMatches(flowId, instanceContext.getInstanceId(), observedFenceToken,
      NON_TERMINAL_STATUSES, ZonedDateTime.now(ZoneId.systemDefault()));
    if (updated == 0) {
      log.warn("Flow reclaim lost the fence race [flowId: {}, observedFenceToken: {}]", flowId, observedFenceToken);
      return false;
    }
    log.info("Flow ownership reclaimed [flowId: {}, fenceToken: {}]", flowId, observedFenceToken + 1);
    return true;
  }

  public boolean reclaimFlow(UUID flowId, Long observedFenceToken) {
    return reclaim(flowId, observedFenceToken);
  }

  @Transactional(readOnly = true)
  public List<UUID> findStaleAsyncFlowIds(ZonedDateTime cutoff) {
    return flowRepository.findIdsAwaitingAsyncBefore(IN_PROGRESS, cutoff);
  }
}

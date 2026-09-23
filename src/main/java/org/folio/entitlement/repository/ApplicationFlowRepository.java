package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationFlowRepository extends AbstractFlowRepository<ApplicationFlowEntity> {

  @Query("SELECT e FROM ApplicationFlowEntity e WHERE e.flowId = :flowId ORDER BY e.startedAt ASC")
  List<ApplicationFlowEntity> findByFlowId(UUID flowId);

  @Query("SELECT e FROM ApplicationFlowEntity e WHERE e.flowId in :flowIds ORDER BY e.startedAt")
  List<ApplicationFlowEntity> findByFlowIds(@Param("flowIds") List<UUID> flowIds);

  @Query(nativeQuery = true, value = """
    SELECT DISTINCT af.* FROM {h-schema}application_flow af
    INNER JOIN (
      SELECT MAX(finished_at) AS finished_at, tenant_id, application_id
      FROM {h-schema}application_flow af
      WHERE af.tenant_id = :tenant_id
        AND af.application_id IN :application_ids
      GROUP BY tenant_id, application_id
    ) laf ON af.finished_at = laf.finished_at
          AND af.tenant_id = laf.tenant_id
          AND af.application_id = laf.application_id""")
  List<ApplicationFlowEntity> findLastFlows(
    @Param("application_ids") Collection<String> applicationIds, @Param("tenant_id") UUID tenantId);

  @Query(nativeQuery = true, value = """
    SELECT DISTINCT af.* FROM {h-schema}application_flow af
    INNER JOIN (
      SELECT MAX(finished_at) AS finished_at, tenant_id, application_name
      FROM {h-schema}application_flow af
      WHERE af.tenant_id = :tenant_id
        AND af.application_name IN :application_names
      GROUP BY tenant_id, application_name
    ) laf ON af.finished_at = laf.finished_at
          AND af.tenant_id = laf.tenant_id
          AND af.application_name = laf.application_name""")
  List<ApplicationFlowEntity> findLastFlowsByApplicationNames(
    @Param("application_names") Collection<String> applicationNames, @Param("tenant_id") UUID tenantId);

  @Modifying
  @Query("DELETE ApplicationFlowEntity entity WHERE entity.flowId = :flowId and entity.status = 'QUEUED'")
  void removeQueuedFlows(@Param("flowId") UUID flowId);

  @Modifying
  @Query("DELETE ApplicationFlowEntity entity WHERE entity.id = :id and entity.status = 'QUEUED'")
  void removeQueuedFlow(@Param("id") UUID id);

  /**
   * Compare-and-set that starts an application flow: refuses when the application flow or its parent flow has
   * already reached a terminal status - both can be failed by the execution timeout while the flow keeps running,
   * and rows queued after the timeout are only protected by the parent flow check.
   */
  @Modifying
  @Query("UPDATE ApplicationFlowEntity e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.id = :id AND e.status IN :currentStatuses "
    + "AND EXISTS (SELECT f.id FROM FlowEntity f WHERE f.id = e.flowId AND f.status IN :currentStatuses)")
  int updateStatusIfCurrentInAndFlowActive(@Param("id") UUID id,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  /**
   * Compare-and-set on the statuses of all application flows of the given flow: the status check is a part of the
   * statement, so a status set concurrently by a finalizer stage cannot be overwritten. {@code finishedAt} is passed
   * in because a bulk update bypasses {@link org.hibernate.annotations.UpdateTimestamp}.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE ApplicationFlowEntity e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.flowId = :flowId AND e.status IN :currentStatuses")
  int updateStatusByFlowIdIfCurrentIn(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  @Override
  @Query("""
    SELECT COUNT(entity) > 0 FROM FlowStageEntity entity
    WHERE entity.flowId = :flowId AND entity.status = :status AND entity.id != :excludedStageId""")
  boolean existsAnyStageByFlowIdAndStatusExcluding(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("excludedStageId") UUID excludedStageId);

  /**
   * Completes an application flow that is waiting on asynchronous stage confirmations.
   *
   * <p>The {@code awaitingAsyncSince IS NOT NULL} predicate is load-bearing. Stage rows are created lazily as each
   * stage starts, so {@code NOT EXISTS(... IN_PROGRESS ...)} on its own only means "nothing is running at this
   * instant" - it is blind to stages that have not begun. Requiring the anchor means the flow's finalizer has
   * already declared the synchronous work complete, which is the only point at which an empty in-progress set
   * genuinely implies there is no work left.</p>
   */
  @Modifying
  @Query("""
    UPDATE ApplicationFlowEntity af
    SET af.status = :status, af.finishedAt = :finishedAt
    WHERE af.id = :id AND af.status IN :currentStatuses
      AND af.awaitingAsyncSince IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM FlowStageEntity s
          WHERE s.flowId = af.id AND s.status IN :currentStatuses)""")
  int updateStatusByIdIfCurrentInAndNoStagesWithStatus(@Param("id") UUID id,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}

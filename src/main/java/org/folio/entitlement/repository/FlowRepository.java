package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowRepository extends AbstractFlowRepository<FlowEntity> {

  @Query("SELECT e.status FROM FlowEntity e WHERE e.id = :flowId")
  Optional<EntityExecutionStatus> findStatusById(@Param("flowId") UUID flowId);

  @Query("SELECT e.fenceToken FROM FlowEntity e WHERE e.id = :flowId")
  Optional<Long> findFenceTokenById(@Param("flowId") UUID flowId);

  /**
   * Finds flows that have been waiting for asynchronous stage confirmations for longer than the given cutoff.
   *
   * <p>Only top-level flows need scanning: a flow cannot be completed while any of its application flows is still
   * in progress, so an application flow stuck on a confirmation always keeps its parent anchored too, and failing
   * the parent cascades down to the application flows and their stages.</p>
   */
  @Query("""
    SELECT f.id FROM FlowEntity f
    WHERE f.status = :status
      AND f.awaitingAsyncSince IS NOT NULL
      AND f.awaitingAsyncSince < :cutoff""")
  List<UUID> findIdsAwaitingAsyncBefore(@Param("status") EntityExecutionStatus status,
    @Param("cutoff") ZonedDateTime cutoff);

  @Override
  @Query("""
    SELECT
      EXISTS (SELECT 1 FROM FlowStageEntity fs
              WHERE fs.flowId = :flowId AND fs.status = :status AND fs.id != :excludedStageId)
      OR EXISTS (SELECT 1 FROM ApplicationFlowEntity af WHERE af.flowId = :flowId AND af.status = :status)
    FROM FlowEntity f
    WHERE f.id = :flowId""")
  boolean existsAnyStageByFlowIdAndStatusExcluding(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("excludedStageId") UUID excludedStageId);

  /**
   * Reclaims a flow using a compare-and-set fence. The token is incremented in the same statement as the ownership
   * change, so concurrent reclaim attempts cannot both win.
   */
  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = org.folio.entitlement.domain.entity.type.EntityExecutionStatus.INTERRUPTED,
        f.ownerInstanceId = :ownerInstanceId, f.fenceToken = f.fenceToken + 1,
        f.finishedAt = :finishedAt
    WHERE f.id = :flowId AND f.fenceToken = :observedFenceToken
      AND f.status IN :currentStatuses""")
  int reclaimIfFenceMatches(@Param("flowId") UUID flowId, @Param("ownerInstanceId") UUID ownerInstanceId,
    @Param("observedFenceToken") Long observedFenceToken,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  /** Writes a root flow status only for the owner holding the observed fence token. */
  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = :status, f.finishedAt = :finishedAt, f.fenceToken = f.fenceToken + 1
    WHERE f.id = :flowId AND f.fenceToken = :observedFenceToken AND f.status IN :currentStatuses""")
  int updateStatusIfFenceMatches(@Param("flowId") UUID flowId, @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt, @Param("observedFenceToken") Long observedFenceToken);

  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = :status, f.finishedAt = :finishedAt, f.fenceToken = f.fenceToken + 1
    WHERE f.id = :flowId AND f.fenceToken = :observedFenceToken
      AND f.status IN :currentStatuses AND f.awaitingAsyncSince IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM FlowStageEntity s
        WHERE s.flowId = f.id AND s.status IN :currentStatuses)
      AND NOT EXISTS (SELECT 1 FROM ApplicationFlowEntity af
        WHERE af.flowId = f.id AND af.status IN :currentStatuses)""")
  int updateStatusByIdIfCurrentInAndNoStagesWithStatusAndFence(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt,
    @Param("observedFenceToken") Long observedFenceToken);

  /** Records the async anchor only while the owner still holds the observed fence token. */
  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.awaitingAsyncSince = :awaitingAsyncSince, f.fenceToken = f.fenceToken + 1
    WHERE f.id = :flowId AND f.fenceToken = :observedFenceToken
      AND f.status = org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS
      AND f.awaitingAsyncSince IS NULL""")
  int markAwaitingAsyncIfFenceMatches(@Param("flowId") UUID flowId,
    @Param("awaitingAsyncSince") ZonedDateTime awaitingAsyncSince,
    @Param("observedFenceToken") Long observedFenceToken);

  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = :status, f.finishedAt = :finishedAt
    WHERE f.id = :flowId AND f.status IN :currentStatuses
      AND f.awaitingAsyncSince IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM FlowStageEntity s
          WHERE s.flowId = f.id AND s.status IN :currentStatuses)
      AND NOT EXISTS (
          SELECT 1 FROM ApplicationFlowEntity af
          WHERE af.flowId = f.id AND af.status IN :currentStatuses)""")
  int updateStatusByIdIfCurrentInAndNoStagesWithStatus(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);
}

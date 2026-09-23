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

  /**
   * Compare-and-set status update for a root flow. Every successful root status write advances the fence token.
   */
  @Override
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE FlowEntity e SET e.status = :status, e.finishedAt = :finishedAt, "
    + "e.fenceToken = e.fenceToken + 1 "
    + "WHERE e.id = :id AND e.status IN :currentStatuses")
  int updateStatusIfCurrentIn(@Param("id") UUID id,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  /**
   * Updates a root flow only when the caller still owns the observed fence token. The token is incremented as part
   * of the same statement, making concurrent reclaim and terminal writes a database compare-and-set operation.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE FlowEntity f SET f.status = :status, f.finishedAt = :finishedAt, "
    + "f.fenceToken = f.fenceToken + 1 "
    + "WHERE f.id = :flowId AND f.status IN :currentStatuses AND f.fenceToken = :fenceToken")
  int updateStatusIfCurrentInAndFenceToken(@Param("flowId") UUID flowId,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt, @Param("fenceToken") Long fenceToken);

  /**
   * Acquires the database row lock for an owner write without changing the fence token. The conditional update
   * makes the result authoritative: a reclaimed owner gets zero rows and must not continue with child writes.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE FlowEntity e SET e.fenceToken = e.fenceToken "
    + "WHERE e.id = :flowId AND e.fenceToken = :fenceToken")
  int guardFenceToken(@Param("flowId") UUID flowId, @Param("fenceToken") Long fenceToken);

  @Query("SELECT e.status FROM FlowEntity e WHERE e.id = :flowId")
  Optional<EntityExecutionStatus> findStatusById(@Param("flowId") UUID flowId);

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
  @Modifying
  @Query("UPDATE FlowEntity e SET e.awaitingAsyncSince = :awaitingAsyncSince, "
    + "e.fenceToken = e.fenceToken + 1 "
    + "WHERE e.id = :id AND e.awaitingAsyncSince IS NULL")
  int markAwaitingAsync(@Param("id") UUID id, @Param("awaitingAsyncSince") ZonedDateTime awaitingAsyncSince);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE FlowEntity e SET e.awaitingAsyncSince = :awaitingAsyncSince, "
    + "e.fenceToken = e.fenceToken + 1 "
    + "WHERE e.id = :id AND e.awaitingAsyncSince IS NULL AND e.fenceToken = :fenceToken")
  int markAwaitingAsyncWithFenceToken(@Param("id") UUID id,
    @Param("awaitingAsyncSince") ZonedDateTime awaitingAsyncSince, @Param("fenceToken") Long fenceToken);

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
   * Completes a top-level flow that is waiting on asynchronous stage confirmations. See
   * {@link ApplicationFlowRepository#updateStatusByIdIfCurrentInAndNoStagesWithStatus} for why the
   * {@code awaitingAsyncSince IS NOT NULL} predicate is required rather than relying on the {@code NOT EXISTS}
   * checks alone.
   */
  @Modifying
  @Query("""
    UPDATE FlowEntity f
    SET f.status = :status, f.finishedAt = :finishedAt, f.fenceToken = f.fenceToken + 1
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

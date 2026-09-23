package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface AbstractFlowRepository<T extends AbstractFlowEntity> extends JpaCqlRepository<T, UUID> {

  boolean existsAnyStageByFlowIdAndStatusExcluding(UUID flowId, EntityExecutionStatus status, UUID excludedStageId);

  /**
   * Compare-and-set on the flow status: the status check is a part of the statement, so a status set concurrently by
   * another writer cannot be overwritten. {@code finishedAt} is passed in because a bulk update bypasses
   * {@link org.hibernate.annotations.UpdateTimestamp}.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE #{#entityName} e SET e.status = :status, e.finishedAt = :finishedAt "
    + "WHERE e.id = :id AND e.status IN :currentStatuses")
  int updateStatusIfCurrentIn(@Param("id") UUID id,
    @Param("status") EntityExecutionStatus status,
    @Param("currentStatuses") Collection<EntityExecutionStatus> currentStatuses,
    @Param("finishedAt") ZonedDateTime finishedAt);

  /**
   * Records that the flow has finished its synchronous work and is now waiting for asynchronous stage
   * confirmations. Until this anchor is set, no inbound result may complete the flow - which is what stops a fast
   * downstream response from finishing a flow whose remaining stages have not started yet and therefore have no
   * rows to be seen.
   *
   * <p>Set-once by design: the {@code IS NULL} guard means a re-entered or retried finalizer cannot push the
   * anchor forward, which would otherwise reset the staleness clock the sweeper measures from. {@code finishedAt}
   * is deliberately not touched here - the flow has not finished.</p>
   *
   * @return 1 when the anchor was written, 0 when it was already set
   */
  @Modifying
  @Query("UPDATE #{#entityName} e SET e.awaitingAsyncSince = :awaitingAsyncSince "
    + "WHERE e.id = :id AND e.awaitingAsyncSince IS NULL")
  int markAwaitingAsync(@Param("id") UUID id, @Param("awaitingAsyncSince") ZonedDateTime awaitingAsyncSince);
}

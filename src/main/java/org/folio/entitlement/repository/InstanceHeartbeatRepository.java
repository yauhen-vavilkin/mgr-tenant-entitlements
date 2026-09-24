package org.folio.entitlement.repository;

import java.time.ZonedDateTime;
import java.util.UUID;
import org.folio.entitlement.domain.entity.InstanceHeartbeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstanceHeartbeatRepository extends JpaRepository<InstanceHeartbeatEntity, UUID> {

  boolean existsByInstanceIdAndLastHeartbeatAfter(UUID instanceId, ZonedDateTime cutoff);

  @Modifying
  @Query(value = "INSERT INTO mte_instance (instance_id, started_at, last_heartbeat) "
    + "VALUES (:instanceId, :startedAt, :lastHeartbeat) "
    + "ON CONFLICT (instance_id) DO UPDATE SET last_heartbeat = EXCLUDED.last_heartbeat",
    nativeQuery = true)
  int upsertHeartbeat(@Param("instanceId") UUID instanceId, @Param("startedAt") ZonedDateTime startedAt,
    @Param("lastHeartbeat") ZonedDateTime lastHeartbeat);

  @Modifying
  @Query("DELETE FROM InstanceHeartbeatEntity e WHERE e.lastHeartbeat < :cutoff")
  int deleteOlderThan(@Param("cutoff") ZonedDateTime cutoff);
}

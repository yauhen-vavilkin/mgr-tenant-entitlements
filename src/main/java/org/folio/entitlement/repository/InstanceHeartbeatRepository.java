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
  @Query("UPDATE InstanceHeartbeatEntity e SET e.lastHeartbeat = :lastHeartbeat WHERE e.instanceId = :instanceId")
  int updateHeartbeat(@Param("instanceId") UUID instanceId, @Param("lastHeartbeat") ZonedDateTime lastHeartbeat);

  @Modifying
  @Query("DELETE FROM InstanceHeartbeatEntity e WHERE e.lastHeartbeat < :cutoff")
  int deleteOlderThan(@Param("cutoff") ZonedDateTime cutoff);
}

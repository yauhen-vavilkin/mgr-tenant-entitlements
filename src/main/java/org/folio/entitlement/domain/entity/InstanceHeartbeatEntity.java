package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "mte_instance")
public class InstanceHeartbeatEntity {

  @Id
  @Column(name = "instance_id")
  private UUID instanceId;

  @Column(name = "started_at", nullable = false, updatable = false)
  private ZonedDateTime startedAt;

  @Column(name = "last_heartbeat", nullable = false)
  private ZonedDateTime lastHeartbeat;
}

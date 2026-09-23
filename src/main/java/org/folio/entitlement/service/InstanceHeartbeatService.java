package org.folio.entitlement.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.InstanceHeartbeatConfigurationProperties;
import org.folio.entitlement.domain.entity.InstanceHeartbeatEntity;
import org.folio.entitlement.repository.InstanceHeartbeatRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceHeartbeatService {

  private final InstanceContext instanceContext;
  private final InstanceHeartbeatRepository repository;
  private final InstanceHeartbeatConfigurationProperties properties;

  /** Registers this process before scheduled heartbeat updates begin. */
  @Transactional
  public void register() {
    try {
      var instance = new InstanceHeartbeatEntity();
      instance.setInstanceId(instanceContext.getInstanceId());
      instance.setStartedAt(instanceContext.getStartedAt());
      instance.setLastHeartbeat(now());
      repository.saveAndFlush(instance);
      log.info("MTE instance registered [instanceId: {}]", instanceContext.getInstanceId());
    } catch (Exception e) {
      log.error("Failed to register MTE instance [instanceId: {}]", instanceContext.getInstanceId(), e);
      throw e;
    }
  }

  /** Refreshes the heartbeat independently from entitlement flow execution threads. */
  @Scheduled(fixedDelayString = "${application.instance-heartbeat.interval:10s}",
    initialDelayString = "${application.instance-heartbeat.interval:10s}")
  @Transactional
  public void updateHeartbeat() {
    try {
      var updated = repository.updateHeartbeat(instanceContext.getInstanceId(), now());
      if (updated == 0) {
        log.error("MTE instance heartbeat was not updated because its record is missing [instanceId: {}]",
          instanceContext.getInstanceId());
      }
    } catch (Exception e) {
      log.error("Failed to update MTE instance heartbeat [instanceId: {}]", instanceContext.getInstanceId(), e);
    }
  }

  /** Removes heartbeat records that have exceeded the configured retention period. */
  @Scheduled(fixedDelayString = "${application.instance-heartbeat.cleanup-interval:24h}")
  @Transactional
  public void cleanup() {
    try {
      var cutoff = now().minus(properties.getRetention());
      var deleted = repository.deleteOlderThan(cutoff);
      log.debug("MTE instance heartbeat cleanup completed [deleted: {}, cutoff: {}]", deleted, cutoff);
    } catch (Exception e) {
      log.error("Failed to clean up MTE instance heartbeats", e);
    }
  }

  @Transactional(readOnly = true)
  public boolean isAlive(UUID instanceId) {
    return repository.existsByInstanceIdAndLastHeartbeatAfter(instanceId, staleCutoff());
  }

  public ZonedDateTime staleCutoff() {
    return now().minus(properties.getStaleThreshold());
  }

  private static ZonedDateTime now() {
    return ZonedDateTime.now(ZoneId.systemDefault());
  }
}

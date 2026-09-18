package org.folio.entitlement.service;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Provides the identity of this MTE process for the lifetime of the application.
 */
@Component
@Log4j2
public class InstanceContext {

  private final UUID instanceId;

  public InstanceContext() {
    instanceId = UUID.randomUUID();
    log.info("MTE instance initialized [instanceId: {}]", instanceId);
  }

  /**
   * Returns the identifier shared by all flows created by this process.
   *
   * @return process instance identifier
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

package org.folio.entitlement.service;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Identifies this MTE process for the lifetime of the application.
 */
@Log4j2
@Component
public class InstanceContext {

  private final UUID instanceId = UUID.randomUUID();

  public InstanceContext() {
    log.info("MTE instance started [instanceId: {}]", instanceId);
  }

  public UUID getInstanceId() {
    return instanceId;
  }
}

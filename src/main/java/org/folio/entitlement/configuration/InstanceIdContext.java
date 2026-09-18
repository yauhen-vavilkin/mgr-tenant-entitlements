package org.folio.entitlement.configuration;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Provides the identity of this MTE process for the lifetime of the application.
 */
@Log4j2
@Component
public class InstanceIdContext {

  private final UUID instanceId = UUID.randomUUID();

  public InstanceIdContext() {
    log.info("MTE instance ID: {}", instanceId);
  }

  /**
   * Returns the identity of this MTE process.
   *
   * @return process instance id
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

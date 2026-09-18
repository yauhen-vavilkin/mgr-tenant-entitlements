package org.folio.entitlement.configuration;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Provides the identity of this MTE process.
 *
 * <p>The context is a Spring singleton, so the identity is generated once and remains unchanged for the
 * lifetime of the process.</p>
 */
@Log4j2
@Component
public class InstanceIdContext {

  private final UUID instanceId;

  public InstanceIdContext() {
    instanceId = UUID.randomUUID();
    log.info("MTE instance initialized [instanceId: {}]", instanceId);
  }

  /**
   * Returns the identity of this MTE process.
   *
   * @return process instance identity
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

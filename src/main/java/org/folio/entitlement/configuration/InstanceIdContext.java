package org.folio.entitlement.configuration;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Identifies this MTE process for the lifetime of the application.
 */
@Log4j2
@Component
public class InstanceIdContext {

  private final UUID instanceId;

  public InstanceIdContext() {
    instanceId = UUID.randomUUID();
    log.info("MTE instance ID initialized [instanceId: {}]", instanceId);
  }

  /**
   * Returns the ID generated for this process.
   *
   * @return process instance ID
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

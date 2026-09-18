package org.folio.entitlement.service.instance;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Provides the identity of this MTE process for its entire application lifetime.
 */
@Component
@Log4j2
public class InstanceContext {

  private final UUID instanceId = UUID.randomUUID();

  public InstanceContext() {
    log.info("MTE instance started [instanceId: {}]", instanceId);
  }

  /**
   * Returns the identity of this process.
   *
   * @return process instance identifier
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

package org.folio.entitlement.service;

import java.util.UUID;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Holds the identity of this MTE process for its entire application lifetime.
 */
@Log4j2
@Getter
@Component
public class InstanceContext {

  private final UUID instanceId;

  public InstanceContext() {
    instanceId = UUID.randomUUID();
    log.info("MTE instance started [instanceId: {}]", instanceId);
  }
}

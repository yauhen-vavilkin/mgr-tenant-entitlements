package org.folio.entitlement.service;

import java.util.UUID;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Application-scoped identity of this MTE process. The id is generated once at startup and never changes.
 */
@Log4j2
@Getter
@Component
public class InstanceContext {

  private final String instanceId;

  public InstanceContext() {
    this(UUID.randomUUID().toString());
  }

  InstanceContext(String instanceId) {
    this.instanceId = instanceId;
    log.info("MTE instance started [instanceId: {}]", instanceId);
  }
}

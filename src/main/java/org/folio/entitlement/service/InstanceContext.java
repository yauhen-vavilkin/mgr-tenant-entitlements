package org.folio.entitlement.service;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * An application-scoped context holding the identity of the current MTE application instance.
 *
 * <p>The identifier is generated once when the application context is created and stays unchanged for the whole
 * process lifetime. It is stamped on every flow created by this instance (see
 * {@link org.folio.entitlement.domain.entity.FlowEntity#getOwnerInstanceId()}), so a later request can distinguish
 * a flow owned by a live process from one abandoned by a crashed or restarted instance.</p>
 */
@Log4j2
@Component
public class InstanceContext {

  private final UUID instanceId;

  public InstanceContext() {
    this.instanceId = UUID.randomUUID();
    log.info("MTE application instance is started [instanceId: {}]", instanceId);
  }

  /**
   * Provides the identifier of the current MTE application instance.
   *
   * @return the instance identifier as {@link UUID}
   */
  public UUID getInstanceId() {
    return instanceId;
  }
}

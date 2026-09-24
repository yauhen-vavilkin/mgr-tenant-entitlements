package org.folio.entitlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstanceHeartbeatInitializer {

  private final InstanceHeartbeatService heartbeatService;

  @EventListener(ApplicationReadyEvent.class)
  public void registerInstance() {
    heartbeatService.register();
  }
}

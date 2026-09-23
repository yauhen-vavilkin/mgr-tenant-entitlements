package org.folio.entitlement.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "application.instance-heartbeat")
public class InstanceHeartbeatConfigurationProperties {

  /**
   * Delay between heartbeat writes for this instance.
   */
  @NotNull
  private Duration interval = Duration.ofSeconds(10);

  /**
   * Maximum age of a heartbeat for an instance to be considered alive.
   */
  @NotNull
  private Duration staleThreshold = Duration.ofSeconds(30);

  /**
   * Delay between cleanup runs.
   */
  @NotNull
  private Duration cleanupInterval = Duration.ofDays(1);

  /**
   * Age after which an instance heartbeat can be removed.
   */
  @NotNull
  private Duration retention = Duration.ofDays(1);
}

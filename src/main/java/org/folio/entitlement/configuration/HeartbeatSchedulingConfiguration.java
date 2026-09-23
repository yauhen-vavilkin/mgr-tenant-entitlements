package org.folio.entitlement.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class HeartbeatSchedulingConfiguration implements SchedulingConfigurer {

  @Bean(name = "taskScheduler", destroyMethod = "shutdown")
  public ThreadPoolTaskScheduler taskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("mte-scheduled-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }

  @Bean(name = "heartbeatTaskScheduler", destroyMethod = "shutdown")
  public ThreadPoolTaskScheduler heartbeatTaskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("mte-heartbeat-");
    scheduler.setWaitForTasksToCompleteOnShutdown(false);
    return scheduler;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.setTaskScheduler(taskScheduler());
  }
}

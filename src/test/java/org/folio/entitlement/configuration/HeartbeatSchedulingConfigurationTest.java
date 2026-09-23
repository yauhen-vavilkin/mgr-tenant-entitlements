package org.folio.entitlement.configuration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.folio.entitlement.repository.InstanceHeartbeatRepository;
import org.folio.entitlement.service.InstanceContext;
import org.folio.entitlement.service.InstanceHeartbeatService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HeartbeatSchedulingConfigurationTest {

  @Mock private InstanceContext instanceContext;
  @Mock private InstanceHeartbeatRepository repository;

  private InstanceHeartbeatService heartbeatService;
  private UUID instanceId;
  private ZonedDateTime startedAt;

  @BeforeEach
  void setUp() {
    instanceId = UUID.randomUUID();
    startedAt = ZonedDateTime.now();
    heartbeatService = new InstanceHeartbeatService(instanceContext, repository,
      new InstanceHeartbeatConfigurationProperties());
  }

  @Test
  void heartbeatExecutesWhenDefaultScheduledWorkIsBlocked() throws InterruptedException {
    var configuration = new HeartbeatSchedulingConfiguration();
    var scheduledScheduler = configuration.taskScheduler();
    var heartbeatScheduler = configuration.heartbeatTaskScheduler();
    var defaultTaskStarted = new CountDownLatch(1);
    var releaseDefaultTask = new CountDownLatch(1);
    var heartbeatFinished = new CountDownLatch(1);
    configureInstance();
    initialize(scheduledScheduler, heartbeatScheduler);

    try {
      scheduledScheduler.execute(() -> blockTask(defaultTaskStarted, releaseDefaultTask));
      assertThat(defaultTaskStarted.await(1, SECONDS)).isTrue();
      heartbeatScheduler.execute(() -> runHeartbeat(heartbeatFinished));

      assertThat(heartbeatFinished.await(1, SECONDS)).isTrue();
      verify(repository).upsertHeartbeat(eq(instanceId), eq(startedAt), any(ZonedDateTime.class));
    } finally {
      releaseDefaultTask.countDown();
      scheduledScheduler.shutdown();
      heartbeatScheduler.shutdown();
    }
  }

  private void configureInstance() {
    org.mockito.Mockito.when(instanceContext.getInstanceId()).thenReturn(instanceId);
    org.mockito.Mockito.when(instanceContext.getStartedAt()).thenReturn(startedAt);
  }

  private void runHeartbeat(CountDownLatch heartbeatFinished) {
    heartbeatService.updateHeartbeat();
    heartbeatFinished.countDown();
  }

  private static void blockTask(CountDownLatch taskStarted, CountDownLatch releaseTask) {
    taskStarted.countDown();
    try {
      releaseTask.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void initialize(ThreadPoolTaskScheduler scheduledScheduler,
    ThreadPoolTaskScheduler heartbeatScheduler) {
    scheduledScheduler.initialize();
    heartbeatScheduler.initialize();
  }
}

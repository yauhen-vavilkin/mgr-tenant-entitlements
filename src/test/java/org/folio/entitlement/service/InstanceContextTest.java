package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class InstanceContextTest {

  @Test
  void getInstanceId_positive_idIsGeneratedOnceAndStableForProcessLifetime() {
    var instanceContext = new InstanceContext();

    var instanceId = instanceContext.getInstanceId();

    assertThat(instanceId).isNotNull();
    assertThat(instanceContext.getInstanceId()).isEqualTo(instanceId);
  }

  @Test
  void getInstanceId_positive_idIsUniquePerProcess() {
    var firstInstanceId = new InstanceContext().getInstanceId();
    var secondInstanceId = new InstanceContext().getInstanceId();

    assertThat(firstInstanceId).isNotNull();
    assertThat(secondInstanceId).isNotNull();
    assertThat(firstInstanceId).isNotEqualTo(secondInstanceId);
  }
}

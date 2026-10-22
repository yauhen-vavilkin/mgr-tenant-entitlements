package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class InstanceContextTest {

  @Test
  void instanceId_positive_uniquePerInstanceAndStable() {
    var first = new InstanceContext();
    var second = new InstanceContext();

    assertThat(first.getInstanceId()).isNotBlank().isNotEqualTo(second.getInstanceId());
    assertThat(first.getInstanceId()).isEqualTo(first.getInstanceId());
  }
}

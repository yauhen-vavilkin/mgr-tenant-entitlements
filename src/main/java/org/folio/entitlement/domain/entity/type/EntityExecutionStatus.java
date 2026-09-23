package org.folio.entitlement.domain.entity.type;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.folio.entitlement.domain.dto.ExecutionStatus;

public enum EntityExecutionStatus {

  QUEUED,
  IN_PROGRESS,
  INTERRUPTED,
  CANCELLED,
  CANCELLATION_FAILED,
  FAILED,
  FINISHED;

  public static final Set<EntityExecutionStatus> NON_TERMINAL_STATUSES =
    Collections.unmodifiableSet(EnumSet.of(QUEUED, IN_PROGRESS));

  /**
   * Creates {@link EntityExecutionStatus} from {@link ExecutionStatus} enum value.
   *
   * @param status - {@link ExecutionStatus} to process
   * @return {@link EntityExecutionStatus} from {@link ExecutionStatus}
   */
  public static EntityExecutionStatus from(ExecutionStatus status) {
    return EntityExecutionStatus.valueOf(status.name());
  }
}

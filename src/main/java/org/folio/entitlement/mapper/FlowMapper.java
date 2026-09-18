package org.folio.entitlement.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;

import java.util.List;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
  componentModel = "spring",
  injectionStrategy = CONSTRUCTOR,
  imports = {EntityExecutionStatus.class}
)
public interface FlowMapper {

  @Mapping(target = "stages", ignore = true)
  @Mapping(target = "applicationFlows", ignore = true)
  Flow map(FlowEntity entity);

  /**
   * The owner instance id is deliberately not mapped from the API representation: it is stamped with the current
   * MTE instance identifier by {@link org.folio.entitlement.service.flow.FlowService#create(Flow)} on persist.
   */
  @Mapping(target = "ownerInstanceId", ignore = true)
  FlowEntity map(Flow entity);

  List<Flow> map(List<FlowEntity> entity);
}

package org.folio.entitlement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.folio.entitlement.domain.entity.type.EntityFlowEntitlementType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "flow")
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FlowEntity extends AbstractFlowEntity {

  /**
   * A reference id to the entity in entitlement table.
   */
  @Column(name = "tenant_id")
  private UUID tenantId;

  /**
   * The MTE instance that created this flow.
   */
  @Column(name = "owner_instance_id")
  private UUID ownerInstanceId;

  /**
   * An entitlement request type for tenant.
   */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type", columnDefinition = "entitlement_flow_type")
  private EntityFlowEntitlementType type;
}

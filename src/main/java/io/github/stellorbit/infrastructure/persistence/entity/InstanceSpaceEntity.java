package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instance_spaces", schema = "stellorbit")
public class InstanceSpaceEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "租户ID不能为空") @Column(name = "tenant_id", nullable = false, length = 120)
  private String tenantId;

  @NotBlank(message = "实例空间编码不能为空") @Column(name = "space_code", nullable = false, length = 160)
  private String spaceCode;

  @NotBlank(message = "实例空间名称不能为空") @Column(name = "space_name", nullable = false, length = 160)
  private String spaceName;

  @NotBlank(message = "组织不能为空") @Column(nullable = false, length = 120)
  private String organization;

  @NotBlank(message = "业务域不能为空") @Column(name = "business_domain", nullable = false, length = 120)
  private String businessDomain;

  @NotBlank(message = "能力域不能为空") @Column(name = "capability_domain", nullable = false, length = 120)
  private String capabilityDomain;

  @NotBlank(message = "环境不能为空") @Column(nullable = false, length = 80)
  private String environment;

  @Column(columnDefinition = "text")
  private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> labels = new LinkedHashMap<>();

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @NotBlank(message = "创建人不能为空") @Column(name = "created_by", nullable = false, length = 120)
  private String createdBy;

  @NotBlank(message = "更新人不能为空") @Column(name = "updated_by", nullable = false, length = 120)
  private String updatedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

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
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "approval_policies", schema = "stellorbit")
public class ApprovalPolicyEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false, length = 120)
  private String tenantId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @Column(name = "application_id")
  private UUID applicationId;

  @NotBlank(message = "审批策略编码不能为空") @Column(name = "policy_code", nullable = false, length = 160)
  private String policyCode;

  @NotBlank(message = "审批策略名称不能为空") @Column(name = "policy_name", nullable = false, length = 160)
  private String policyName;

  @Column(name = "resource_type", nullable = false, length = 80)
  private String resourceType = "RULE_RELEASE";

  @Column(name = "required_approvals", nullable = false)
  private Integer requiredApprovals = 1;

  @Column(name = "allow_self_approval", nullable = false)
  private Boolean allowSelfApproval = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "approver_roles", nullable = false, columnDefinition = "jsonb")
  private List<Object> approverRoles = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "approver_users", nullable = false, columnDefinition = "jsonb")
  private List<Object> approverUsers = new ArrayList<>();

  @Column(name = "policy_status", nullable = false, length = 32)
  private String policyStatus = "ACTIVE";

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

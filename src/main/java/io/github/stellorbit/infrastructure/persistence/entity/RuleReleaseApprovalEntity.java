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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rule_release_approvals", schema = "stellorbit")
public class RuleReleaseApprovalEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @Column(name = "approval_policy_id")
  private UUID approvalPolicyId;

  @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false, length = 120)
  private String tenantId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "approval_status", nullable = false, length = 32)
  private String approvalStatus = "PENDING";

  @Column(name = "required_approvals", nullable = false)
  private Integer requiredApprovals = 1;

  @Column(name = "approved_count", nullable = false)
  private Integer approvedCount = 0;

  @Column(name = "rejected_count", nullable = false)
  private Integer rejectedCount = 0;

  @NotBlank(message = "提交人不能为空") @Column(name = "submitted_by", nullable = false, length = 120)
  private String submittedBy;

  @NotBlank(message = "提交原因不能为空") @Column(name = "submitted_reason", nullable = false, columnDefinition = "text")
  private String submittedReason;

  @CreationTimestamp
  @Column(name = "submitted_at", nullable = false, updatable = false)
  private OffsetDateTime submittedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

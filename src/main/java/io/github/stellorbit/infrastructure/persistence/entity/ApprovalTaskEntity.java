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
@Table(name = "approval_tasks", schema = "stellorbit")
public class ApprovalTaskEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "审批ID不能为空") @Column(name = "approval_id", nullable = false)
  private UUID approvalId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false, length = 120)
  private String tenantId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "task_status", nullable = false, length = 32)
  private String taskStatus = "PENDING";

  @NotBlank(message = "审批人不能为空") @Column(nullable = false, length = 120)
  private String approver;

  @Column(name = "approver_role", length = 120)
  private String approverRole;

  @Column(columnDefinition = "text")
  private String reason;

  @Column(name = "action_at")
  private OffsetDateTime actionAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

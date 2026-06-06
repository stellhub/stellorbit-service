package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rate_limit_quota_assignments", schema = "stellorbit")
public class RateLimitQuotaAssignmentEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "限流规则ID不能为空") @Column(name = "rate_limit_rule_id", nullable = false)
  private UUID rateLimitRuleId;

  @NotNull(message = "配额策略ID不能为空") @Column(name = "quota_policy_id", nullable = false)
  private UUID quotaPolicyId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotBlank(message = "客户端ID不能为空") @Column(name = "client_id", nullable = false, length = 160)
  private String clientId;

  @NotBlank(message = "限流Key哈希不能为空") @Column(name = "limit_key_hash", nullable = false, length = 128)
  private String limitKeyHash;

  @Column(name = "assigned_quota", nullable = false)
  private Long assignedQuota;

  @Column(name = "used_quota", nullable = false)
  private Long usedQuota = 0L;

  @Column(name = "remaining_quota", nullable = false)
  private Long remainingQuota;

  @Column(name = "lease_version", nullable = false)
  private Long leaseVersion;

  @Column(name = "lease_status", nullable = false, length = 32)
  private String leaseStatus = "ACTIVE";

  @Column(name = "assigned_at", nullable = false)
  private OffsetDateTime assignedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

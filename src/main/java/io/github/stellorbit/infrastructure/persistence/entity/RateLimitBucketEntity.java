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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rate_limit_buckets", schema = "stellorbit")
public class RateLimitBucketEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotNull(message = "限流规则ID不能为空") @Column(name = "rate_limit_rule_id", nullable = false)
  private UUID rateLimitRuleId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotBlank(message = "限流Key哈希不能为空") @Column(name = "limit_key_hash", nullable = false, length = 128)
  private String limitKeyHash;

  @NotBlank(message = "限流Key不能为空") @Column(name = "limit_key", nullable = false, columnDefinition = "text")
  private String limitKey;

  @NotBlank(message = "窗口策略不能为空") @Column(name = "window_strategy", nullable = false, length = 48)
  private String windowStrategy;

  @NotNull(message = "窗口开始时间不能为空") @Column(name = "window_start_at", nullable = false)
  private OffsetDateTime windowStartAt;

  @NotNull(message = "窗口结束时间不能为空") @Column(name = "window_end_at", nullable = false)
  private OffsetDateTime windowEndAt;

  @NotNull(message = "配额不能为空") @Column(nullable = false)
  private Long quota;

  @Column(name = "used_permits", nullable = false)
  private Long usedPermits = 0L;

  @NotNull(message = "剩余配额不能为空") @Column(name = "remaining_permits", nullable = false)
  private Long remainingPermits;

  @NotNull(message = "重置时间不能为空") @Column(name = "reset_at", nullable = false)
  private OffsetDateTime resetAt;

  @NotNull(message = "过期时间不能为空") @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

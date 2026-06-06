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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rate_limit_decisions", schema = "stellorbit")
public class RateLimitDecisionEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "bucket_id")
  private UUID bucketId;

  @Column(name = "assignment_id")
  private UUID assignmentId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotNull(message = "限流规则ID不能为空") @Column(name = "rate_limit_rule_id", nullable = false)
  private UUID rateLimitRuleId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @Column(name = "request_id", length = 160)
  private String requestId;

  @Column(name = "client_id", length = 160)
  private String clientId;

  @NotBlank(message = "限流Key哈希不能为空") @Column(name = "limit_key_hash", nullable = false, length = 128)
  private String limitKeyHash;

  @Column(name = "requested_permits", nullable = false)
  private Long requestedPermits = 1L;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model_request_units", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> modelRequestUnits = new LinkedHashMap<>();

  @NotNull(message = "判定结果不能为空") @Column(nullable = false)
  private Boolean allowed;

  @Column(name = "remaining_permits")
  private Long remainingPermits;

  @Column(name = "retry_after_millis")
  private Long retryAfterMillis;

  @Column(name = "fallback_used", nullable = false)
  private Boolean fallbackUsed = false;

  @Column(name = "decision_reason", length = 160)
  private String decisionReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

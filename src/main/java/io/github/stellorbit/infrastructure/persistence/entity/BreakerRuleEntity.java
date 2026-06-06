package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
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
@Table(name = "breaker_rules", schema = "stellorbit")
public class BreakerRuleEntity implements Identifiable {

  @Id
  @Column(name = "governance_rule_id", nullable = false)
  private UUID id;

  @NotBlank(message = "熔断类型不能为空") @Column(name = "breaker_type", nullable = false, length = 48)
  private String breakerType;

  @NotBlank(message = "协议不能为空") @Column(nullable = false, length = 32)
  private String protocol;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "target_selector", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> targetSelector = new LinkedHashMap<>();

  @Column(name = "window_type", length = 32)
  private String windowType;

  @Column(name = "window_size")
  private Long windowSize;

  @Column(name = "minimum_calls")
  private Long minimumCalls;

  @Column(name = "failure_rate_threshold", precision = 7, scale = 4)
  private BigDecimal failureRateThreshold;

  @Column(name = "slow_call_rate_threshold", precision = 7, scale = 4)
  private BigDecimal slowCallRateThreshold;

  @Column(name = "slow_call_duration_millis")
  private Long slowCallDurationMillis;

  @Column(name = "open_state_wait_millis")
  private Long openStateWaitMillis;

  @Column(name = "permitted_half_open_calls")
  private Long permittedHalfOpenCalls;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "connection_pool_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> connectionPoolPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "outlier_detection_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> outlierDetectionPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "retry_budget_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> retryBudgetPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "exception_record_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> exceptionRecordPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "exception_ignore_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> exceptionIgnorePolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fallback_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> fallbackPolicy = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

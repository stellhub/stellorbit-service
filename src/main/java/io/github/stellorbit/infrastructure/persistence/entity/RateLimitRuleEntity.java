package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
@Table(name = "rate_limit_rules", schema = "stellorbit")
public class RateLimitRuleEntity implements Identifiable {

  @Id
  @Column(name = "governance_rule_id", nullable = false)
  private UUID id;

  @NotBlank(message = "限流模式不能为空") @Column(name = "limit_mode", nullable = false, length = 48)
  private String limitMode = "QPS";

  @NotBlank(message = "限流类型不能为空") @Column(name = "limit_type", nullable = false, length = 48)
  private String limitType;

  @NotBlank(message = "限流算法不能为空") @Column(name = "limit_algorithm", nullable = false, length = 48)
  private String limitAlgorithm;

  @Column(name = "traffic_protocol", nullable = false, length = 32)
  private String trafficProtocol = "ANY";

  @Column(name = "enforcement_mode", nullable = false, length = 48)
  private String enforcementMode = "LOCAL";

  @Column(name = "execution_location", nullable = false, length = 48)
  private String executionLocation = "APPLICATION";

  @Column(name = "coordination_mode", nullable = false, length = 48)
  private String coordinationMode = "LOCAL_ONLY";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "target_selector", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> targetSelector = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_matcher", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> requestMatcher = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "key_extractor", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> keyExtractor = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Object> dimensions = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "quota_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> quotaConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "window_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> windowConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "burst_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> burstConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "concurrency_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> concurrencyConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "hotspot_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> hotspotConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> customPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model_limit_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> modelLimitConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fallback_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> fallbackPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> responsePolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "observability_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> observabilityConfig = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shadow_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> shadowConfig = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

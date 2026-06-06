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
@Table(name = "rate_limit_quota_policies", schema = "stellorbit")
public class RateLimitQuotaPolicyEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "限流规则ID不能为空") @Column(name = "rate_limit_rule_id", nullable = false)
  private UUID rateLimitRuleId;

  @NotBlank(message = "配额分配算法不能为空") @Column(name = "allocation_algorithm", nullable = false, length = 48)
  private String allocationAlgorithm;

  @NotNull(message = "配额租约TTL不能为空") @Column(name = "assignment_ttl_millis", nullable = false)
  private Long assignmentTtlMillis;

  @NotNull(message = "上报间隔不能为空") @Column(name = "report_interval_millis", nullable = false)
  private Long reportIntervalMillis;

  @NotNull(message = "重平衡间隔不能为空") @Column(name = "rebalance_interval_millis", nullable = false)
  private Long rebalanceIntervalMillis;

  @Column(name = "max_overdraft_ratio", nullable = false, precision = 7, scale = 4)
  private BigDecimal maxOverdraftRatio = BigDecimal.ZERO;

  @Column(name = "hotspot_shard_count", nullable = false)
  private Integer hotspotShardCount = 1;

  @Column(name = "min_assignment_quota", nullable = false)
  private Long minAssignmentQuota = 0L;

  @Column(name = "max_assignment_quota")
  private Long maxAssignmentQuota;

  @Column(name = "failover_strategy", nullable = false, length = 48)
  private String failoverStrategy = "LAST_ASSIGNMENT";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "algorithm_config", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> algorithmConfig = new LinkedHashMap<>();

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
}

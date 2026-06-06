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
@Table(name = "route_rules", schema = "stellorbit")
public class RouteRuleEntity implements Identifiable {

  @Id
  @Column(name = "governance_rule_id", nullable = false)
  private UUID id;

  @NotBlank(message = "路由类型不能为空") @Column(name = "route_type", nullable = false, length = 48)
  private String routeType;

  @Column(name = "traffic_direction", nullable = false, length = 32)
  private String trafficDirection = "EAST_WEST";

  @NotBlank(message = "协议不能为空") @Column(nullable = false, length = 32)
  private String protocol;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Object> gateways = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Object> hosts = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_selector", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> sourceSelector = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "match_conditions", nullable = false, columnDefinition = "jsonb")
  private List<Object> matchConditions = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Object> destinations = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "route_action", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> routeAction = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rewrite_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rewritePolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "redirect_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> redirectPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mirror_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> mirrorPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "fault_injection_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> faultInjectionPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "timeout_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> timeoutPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "retry_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> retryPolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "load_balance_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> loadBalancePolicy = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "locality_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> localityPolicy = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rate_limit_usage_reports", schema = "stellorbit")
public class RateLimitUsageReportEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "assignment_id")
  private UUID assignmentId;

  @NotNull(message = "限流规则ID不能为空") @Column(name = "rate_limit_rule_id", nullable = false)
  private UUID rateLimitRuleId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotBlank(message = "客户端ID不能为空") @Column(name = "client_id", nullable = false, length = 160)
  private String clientId;

  @NotBlank(message = "限流Key哈希不能为空") @Column(name = "limit_key_hash", nullable = false, length = 128)
  private String limitKeyHash;

  @Column(name = "reported_used", nullable = false)
  private Long reportedUsed;

  @Column(name = "reported_allowed", nullable = false)
  private Long reportedAllowed = 0L;

  @Column(name = "reported_rejected", nullable = false)
  private Long reportedRejected = 0L;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model_usage", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> modelUsage = new LinkedHashMap<>();

  @Column(name = "report_window_start", nullable = false)
  private OffsetDateTime reportWindowStart;

  @Column(name = "report_window_end", nullable = false)
  private OffsetDateTime reportWindowEnd;

  @Column(name = "reported_at", nullable = false)
  private OffsetDateTime reportedAt;
}

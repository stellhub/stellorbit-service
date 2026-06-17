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
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "release_items", schema = "stellorbit")
public class ReleaseItemEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotNull(message = "规则ID不能为空") @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @NotBlank(message = "规则类型不能为空") @Column(name = "rule_type", nullable = false, length = 48)
  private String ruleType;

  @NotBlank(message = "规则编码不能为空") @Column(name = "rule_code", nullable = false, length = 160)
  private String ruleCode;

  @NotBlank(message = "规则名称不能为空") @Column(name = "rule_name", nullable = false, length = 160)
  private String ruleName;

  @NotNull(message = "草稿版本不能为空") @Column(name = "draft_version", nullable = false)
  private Long draftVersion;

  @Column(name = "schema_version", nullable = false, length = 64)
  private String schemaVersion = "stellorbit.governance.v1";

  @Column(name = "protocol_version", nullable = false, length = 64)
  private String protocolVersion = "stellorbit.runtime.protocol.v1";

  @Column(name = "min_client_version", length = 80)
  private String minClientVersion;

  @Column(name = "max_client_version", length = 80)
  private String maxClientVersion;

  @Column(name = "compatibility_status", nullable = false, length = 32)
  private String compatibilityStatus = "COMPATIBLE";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "compatibility_messages", nullable = false, columnDefinition = "jsonb")
  private List<Object> compatibilityMessages = new ArrayList<>();

  @NotNull(message = "优先级不能为空") @Column(nullable = false)
  private Integer priority;

  @NotBlank(message = "CUE规则原文不能为空") @Column(name = "cue_source", nullable = false, columnDefinition = "text")
  private String cueSource;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "runtime_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> runtimeSnapshotJson;

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

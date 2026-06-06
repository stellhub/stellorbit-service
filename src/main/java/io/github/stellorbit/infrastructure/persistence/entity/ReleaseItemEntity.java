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

  @NotNull(message = "优先级不能为空") @Column(nullable = false)
  private Integer priority;

  @NotBlank(message = "CUE规则原文不能为空") @Column(name = "cue_source", nullable = false, columnDefinition = "text")
  private String cueSource;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "runtime_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> runtimeSnapshotJson;

  @Column(name = "runtime_snapshot_bytes")
  private byte[] runtimeSnapshotBytes;

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

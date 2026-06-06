package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "governance_rules", schema = "stellorbit")
public class GovernanceRuleEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotBlank(message = "规则编码不能为空") @Column(name = "rule_code", nullable = false, length = 160)
  private String ruleCode;

  @NotBlank(message = "规则名称不能为空") @Column(name = "rule_name", nullable = false, length = 160)
  private String ruleName;

  @NotBlank(message = "规则类型不能为空") @Column(name = "rule_type", nullable = false, length = 48)
  private String ruleType;

  @Column(name = "source_format", nullable = false, length = 32)
  private String sourceFormat = "CUE";

  @Column(name = "runtime_format", nullable = false, length = 32)
  private String runtimeFormat = "JSON";

  @NotBlank(message = "CUE规则原文不能为空") @Column(name = "cue_source", nullable = false, columnDefinition = "text")
  private String cueSource;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "runtime_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> runtimeSnapshotJson;

  @Column(name = "runtime_snapshot_bytes")
  private byte[] runtimeSnapshotBytes;

  @Column(length = 128)
  private String checksum;

  @Column(nullable = false)
  private Integer priority = 1000;

  @Column(nullable = false)
  private Boolean enabled = true;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(name = "draft_version", nullable = false)
  private Long draftVersion = 1L;

  @Column(name = "latest_release_id")
  private UUID latestReleaseId;

  @Column(columnDefinition = "text")
  private String description;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Object> tags = new ArrayList<>();

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

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

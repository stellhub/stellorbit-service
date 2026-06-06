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
@Table(name = "cue_schema_versions", schema = "stellorbit")
public class CueSchemaVersionEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "规则类型不能为空") @Column(name = "rule_type", nullable = false, length = 48)
  private String ruleType;

  @NotBlank(message = "CUE Schema版本不能为空") @Column(name = "schema_version", nullable = false, length = 64)
  private String schemaVersion;

  @NotBlank(message = "CUE Schema名称不能为空") @Column(name = "schema_name", nullable = false, length = 160)
  private String schemaName;

  @NotBlank(message = "CUE Schema不能为空") @Column(name = "cue_schema", nullable = false, columnDefinition = "text")
  private String cueSchema;

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(columnDefinition = "text")
  private String description;

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

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

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
@Table(name = "rule_validations", schema = "stellorbit")
public class RuleValidationEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "规则ID不能为空") @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @NotNull(message = "草稿版本不能为空") @Column(name = "draft_version", nullable = false)
  private Long draftVersion;

  @Column(name = "source_format", nullable = false, length = 32)
  private String sourceFormat = "CUE";

  @NotBlank(message = "校验状态不能为空") @Column(name = "validation_status", nullable = false, length = 32)
  private String validationStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "normalized_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> normalizedSnapshotJson;

  @Column(name = "normalized_snapshot_bytes")
  private byte[] normalizedSnapshotBytes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "error_messages", nullable = false, columnDefinition = "jsonb")
  private List<String> errorMessages = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "warning_messages", nullable = false, columnDefinition = "jsonb")
  private List<String> warningMessages = new ArrayList<>();

  @NotBlank(message = "校验人不能为空") @Column(name = "validated_by", nullable = false, length = 120)
  private String validatedBy;

  @CreationTimestamp
  @Column(name = "validated_at", nullable = false, updatable = false)
  private OffsetDateTime validatedAt;
}

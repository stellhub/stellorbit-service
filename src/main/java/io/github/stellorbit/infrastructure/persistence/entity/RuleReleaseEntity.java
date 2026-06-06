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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rule_releases", schema = "stellorbit")
public class RuleReleaseEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotNull(message = "发布版本不能为空") @Column(name = "release_version", nullable = false)
  private Long releaseVersion;

  @NotBlank(message = "发布名称不能为空") @Column(name = "release_name", nullable = false, length = 160)
  private String releaseName;

  @Column(name = "release_status", nullable = false, length = 32)
  private String releaseStatus = "CREATED";

  @Column(name = "idempotency_key", length = 160)
  private String idempotencyKey;

  @Column(name = "source_format", nullable = false, length = 32)
  private String sourceFormat = "CUE";

  @Column(name = "runtime_format", nullable = false, length = 32)
  private String runtimeFormat = "JSON";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "release_snapshot_json", columnDefinition = "jsonb")
  private Map<String, Object> releaseSnapshotJson;

  @Column(name = "release_snapshot_bytes")
  private byte[] releaseSnapshotBytes;

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @Column(name = "rollback_from_release_id")
  private UUID rollbackFromReleaseId;

  @Column(name = "release_note", columnDefinition = "text")
  private String releaseNote;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount = 0;

  @Column(name = "max_retry_count", nullable = false)
  private Integer maxRetryCount = 3;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failure_details", nullable = false, columnDefinition = "jsonb")
  private List<Object> failureDetails = new ArrayList<>();

  @Column(name = "recovery_status", nullable = false, length = 32)
  private String recoveryStatus = "NONE";

  @Column(name = "recovered_by", length = 120)
  private String recoveredBy;

  @Column(name = "recovered_at")
  private OffsetDateTime recoveredAt;

  @Column(name = "recovery_note", columnDefinition = "text")
  private String recoveryNote;

  @NotBlank(message = "创建人不能为空") @Column(name = "created_by", nullable = false, length = 120)
  private String createdBy;

  @Column(name = "published_by", length = 120)
  private String publishedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

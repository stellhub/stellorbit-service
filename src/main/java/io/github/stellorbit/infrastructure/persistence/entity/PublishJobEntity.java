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
@Table(name = "publish_jobs", schema = "stellorbit")
public class PublishJobEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @Column(name = "publish_record_id")
  private UUID publishRecordId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotBlank(message = "发布任务类型不能为空") @Column(name = "job_type", nullable = false, length = 48)
  private String jobType;

  @Column(name = "job_status", nullable = false, length = 32)
  private String jobStatus = "PENDING";

  @NotBlank(message = "幂等键不能为空") @Column(name = "idempotency_key", nullable = false, length = 220)
  private String idempotencyKey;

  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount = 0;

  @Column(name = "max_attempts", nullable = false)
  private Integer maxAttempts = 3;

  @Column(name = "next_run_at", nullable = false)
  private OffsetDateTime nextRunAt;

  @Column(name = "locked_by", length = 120)
  private String lockedBy;

  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;

  @Column(name = "last_attempt_at")
  private OffsetDateTime lastAttemptAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failure_details", nullable = false, columnDefinition = "jsonb")
  private List<Object> failureDetails = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadMetadata = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

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
@Table(name = "stellnula_publish_records", schema = "stellorbit")
public class StellnulaPublishRecordEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotBlank(message = "发布类型不能为空") @Column(name = "publish_kind", nullable = false, length = 48)
  private String publishKind;

  @NotBlank(message = "命名空间不能为空") @Column(name = "namespace_code", nullable = false, length = 160)
  private String namespaceCode;

  @NotBlank(message = "配置分组不能为空") @Column(name = "config_group", nullable = false, length = 160)
  private String configGroup;

  @NotBlank(message = "配置键不能为空") @Column(name = "config_key", nullable = false, length = 360)
  private String configKey;

  @NotBlank(message = "dataId不能为空") @Column(name = "data_id", nullable = false, length = 360)
  private String dataId;

  @Column(name = "content_type", nullable = false, length = 80)
  private String contentType = "application/json";

  @Column(name = "runtime_format", nullable = false, length = 32)
  private String runtimeFormat = "JSON";

  @Column(name = "payload_text", columnDefinition = "text")
  private String payloadText;

  @Column(name = "payload_bytes")
  private byte[] payloadBytes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadMetadata = new LinkedHashMap<>();

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @Column(name = "target_version", length = 120)
  private String targetVersion;

  @Column(name = "publish_status", nullable = false, length = 32)
  private String publishStatus = "PENDING";

  @Column(name = "idempotency_key", length = 160)
  private String idempotencyKey;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount = 0;

  @Column(name = "max_retry_count", nullable = false)
  private Integer maxRetryCount = 3;

  @Column(name = "next_retry_at")
  private OffsetDateTime nextRetryAt;

  @Column(name = "last_attempt_at")
  private OffsetDateTime lastAttemptAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "failure_details", nullable = false, columnDefinition = "jsonb")
  private List<Object> failureDetails = new ArrayList<>();

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "recovered_by", length = 120)
  private String recoveredBy;

  @Column(name = "recovered_at")
  private OffsetDateTime recoveredAt;

  @Column(name = "recovery_note", columnDefinition = "text")
  private String recoveryNote;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}

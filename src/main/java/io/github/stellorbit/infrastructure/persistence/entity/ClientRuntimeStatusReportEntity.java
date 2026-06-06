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
@Table(name = "client_runtime_status_reports", schema = "stellorbit")
public class ClientRuntimeStatusReportEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @Column(name = "release_id")
  private UUID releaseId;

  @Column(name = "release_version")
  private Long releaseVersion;

  @NotBlank(message = "客户端ID不能为空") @Column(name = "client_id", nullable = false, length = 160)
  private String clientId;

  @NotBlank(message = "客户端版本不能为空") @Column(name = "client_version", nullable = false, length = 80)
  private String clientVersion;

  @NotBlank(message = "协议版本不能为空") @Column(name = "protocol_version", nullable = false, length = 64)
  private String protocolVersion;

  @NotBlank(message = "快照Schema版本不能为空") @Column(name = "snapshot_schema_version", nullable = false, length = 64)
  private String snapshotSchemaVersion;

  @NotBlank(message = "运行时格式不能为空") @Column(name = "runtime_format", nullable = false, length = 32)
  private String runtimeFormat;

  @NotBlank(message = "生效状态不能为空") @Column(name = "effective_status", nullable = false, length = 32)
  private String effectiveStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rule_statuses", nullable = false, columnDefinition = "jsonb")
  private List<Object> ruleStatuses = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "error_details", nullable = false, columnDefinition = "jsonb")
  private List<Object> errorDetails = new ArrayList<>();

  @Column(name = "reported_at", nullable = false)
  private OffsetDateTime reportedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

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
import java.util.LinkedHashMap;
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
@Table(name = "client_runtime_sessions", schema = "stellorbit")
public class ClientRuntimeSessionEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

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

  @Column(name = "current_release_id")
  private UUID currentReleaseId;

  @Column(name = "current_release_version")
  private Long currentReleaseVersion;

  @Column(name = "client_address", length = 240)
  private String clientAddress;

  @Column(length = 120)
  private String zone;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> labels = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata = new LinkedHashMap<>();

  @Column(name = "session_status", nullable = false, length = 32)
  private String sessionStatus = "ONLINE";

  @CreationTimestamp
  @Column(name = "first_seen_at", nullable = false, updatable = false)
  private OffsetDateTime firstSeenAt;

  @Column(name = "last_heartbeat_at", nullable = false)
  private OffsetDateTime lastHeartbeatAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion = 0L;
}

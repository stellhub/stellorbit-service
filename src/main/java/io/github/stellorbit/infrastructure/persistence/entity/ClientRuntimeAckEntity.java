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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "client_runtime_acks", schema = "stellorbit")
public class ClientRuntimeAckEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotNull(message = "发布版本不能为空") @Column(name = "release_version", nullable = false)
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

  @NotBlank(message = "ACK状态不能为空") @Column(name = "ack_status", nullable = false, length = 32)
  private String ackStatus;

  @NotBlank(message = "校验和不能为空") @Column(nullable = false, length = 128)
  private String checksum;

  @Column(columnDefinition = "text")
  private String message;

  @Column(name = "applied_at")
  private OffsetDateTime appliedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}

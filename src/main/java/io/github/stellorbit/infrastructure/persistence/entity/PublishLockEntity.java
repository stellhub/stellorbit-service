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
@Table(name = "publish_locks", schema = "stellorbit")
public class PublishLockEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @NotNull(message = "应用ID不能为空") @Column(name = "application_id", nullable = false)
  private UUID applicationId;

  @NotNull(message = "发布ID不能为空") @Column(name = "release_id", nullable = false)
  private UUID releaseId;

  @NotBlank(message = "发布锁键不能为空") @Column(name = "lock_key", nullable = false, length = 220)
  private String lockKey;

  @Column(name = "lock_status", nullable = false, length = 32)
  private String lockStatus = "ACTIVE";

  @NotBlank(message = "加锁人不能为空") @Column(name = "locked_by", nullable = false, length = 120)
  private String lockedBy;

  @Column(name = "locked_at", nullable = false)
  private OffsetDateTime lockedAt;

  @Column(name = "heartbeat_at", nullable = false)
  private OffsetDateTime heartbeatAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "released_at")
  private OffsetDateTime releasedAt;

  @Column(name = "release_reason", columnDefinition = "text")
  private String releaseReason;

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

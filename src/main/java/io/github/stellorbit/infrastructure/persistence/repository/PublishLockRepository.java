package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.PublishLockEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishLockRepository extends JpaRepository<PublishLockEntity, UUID> {

  Optional<PublishLockEntity> findByLockKeyAndLockStatus(String lockKey, String lockStatus);

  Optional<PublishLockEntity> findByReleaseIdAndLockStatus(UUID releaseId, String lockStatus);

  List<PublishLockEntity> findByLockStatusAndExpiresAtBefore(
      String lockStatus, OffsetDateTime expiresAt);
}

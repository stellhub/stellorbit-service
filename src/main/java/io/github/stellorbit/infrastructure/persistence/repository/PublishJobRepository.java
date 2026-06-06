package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.PublishJobEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishJobRepository extends JpaRepository<PublishJobEntity, UUID> {

  Optional<PublishJobEntity> findByIdempotencyKey(String idempotencyKey);

  List<PublishJobEntity> findByJobStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAscCreatedAtAsc(
      Collection<String> statuses, OffsetDateTime nextRunAt, Pageable pageable);

  List<PublishJobEntity> findByJobStatusAndLockedAtBefore(
      String jobStatus, OffsetDateTime lockedAt);

  List<PublishJobEntity> findByReleaseIdAndJobStatusIn(
      UUID releaseId, Collection<String> jobStatuses);
}

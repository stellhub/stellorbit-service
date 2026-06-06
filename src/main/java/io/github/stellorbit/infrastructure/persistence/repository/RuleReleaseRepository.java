package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RuleReleaseRepository
    extends JpaRepository<RuleReleaseEntity, UUID>, JpaSpecificationExecutor<RuleReleaseEntity> {

  boolean existsByInstanceSpaceIdAndApplicationIdAndReleaseVersion(
      UUID instanceSpaceId, UUID applicationId, Long releaseVersion);

  Optional<RuleReleaseEntity> findByInstanceSpaceIdAndApplicationIdAndIdempotencyKey(
      UUID instanceSpaceId, UUID applicationId, String idempotencyKey);

  Optional<RuleReleaseEntity> findTopByInstanceSpaceIdAndApplicationIdOrderByReleaseVersionDesc(
      UUID instanceSpaceId, UUID applicationId);

  List<RuleReleaseEntity>
      findByInstanceSpaceIdAndApplicationIdAndReleaseStatusInOrderByReleaseVersionDesc(
          UUID instanceSpaceId, UUID applicationId, List<String> releaseStatuses);

  List<RuleReleaseEntity> findByReleaseStatusAndUpdatedAtBefore(
      String releaseStatus, OffsetDateTime updatedAt);
}

package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseApprovalEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleReleaseApprovalRepository
    extends JpaRepository<RuleReleaseApprovalEntity, UUID> {

  Optional<RuleReleaseApprovalEntity> findByReleaseId(UUID releaseId);
}

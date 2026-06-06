package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ReleaseItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseItemRepository extends JpaRepository<ReleaseItemEntity, UUID> {

  List<ReleaseItemEntity> findByReleaseId(UUID releaseId);
}

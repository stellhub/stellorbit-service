package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.StellnulaPublishRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StellnulaPublishRecordRepository
    extends JpaRepository<StellnulaPublishRecordEntity, UUID> {

  List<StellnulaPublishRecordEntity> findByReleaseId(UUID releaseId);

  List<StellnulaPublishRecordEntity> findByReleaseIdOrderByCreatedAtAsc(UUID releaseId);

  Optional<StellnulaPublishRecordEntity> findByIdAndReleaseId(UUID id, UUID releaseId);
}

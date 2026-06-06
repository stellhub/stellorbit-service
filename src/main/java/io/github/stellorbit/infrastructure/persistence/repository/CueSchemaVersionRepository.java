package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.CueSchemaVersionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CueSchemaVersionRepository extends JpaRepository<CueSchemaVersionEntity, UUID> {

  Optional<CueSchemaVersionEntity> findFirstByRuleTypeAndStatusOrderByCreatedAtDesc(
      String ruleType, String status);
}

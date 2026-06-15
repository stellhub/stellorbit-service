package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RuntimeTableRetentionPolicyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeTableRetentionPolicyRepository
    extends JpaRepository<RuntimeTableRetentionPolicyEntity, UUID> {

  Optional<RuntimeTableRetentionPolicyEntity> findByTableName(String tableName);
}

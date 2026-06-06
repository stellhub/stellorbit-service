package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeAckEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRuntimeAckRepository extends JpaRepository<ClientRuntimeAckEntity, UUID> {

  Optional<ClientRuntimeAckEntity> findByReleaseIdAndClientId(UUID releaseId, String clientId);
}

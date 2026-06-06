package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeSessionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRuntimeSessionRepository
    extends JpaRepository<ClientRuntimeSessionEntity, UUID> {

  Optional<ClientRuntimeSessionEntity> findByInstanceSpaceIdAndApplicationIdAndClientId(
      UUID instanceSpaceId, UUID applicationId, String clientId);

  List<ClientRuntimeSessionEntity> findByInstanceSpaceIdAndApplicationIdOrderByLastHeartbeatAtDesc(
      UUID instanceSpaceId, UUID applicationId);

  List<ClientRuntimeSessionEntity> findByLastHeartbeatAtBefore(OffsetDateTime lastHeartbeatAt);
}

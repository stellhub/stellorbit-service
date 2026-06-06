package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeStatusReportEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRuntimeStatusReportRepository
    extends JpaRepository<ClientRuntimeStatusReportEntity, UUID> {

  List<ClientRuntimeStatusReportEntity>
      findTop20ByInstanceSpaceIdAndApplicationIdOrderByCreatedAtDesc(
          UUID instanceSpaceId, UUID applicationId);
}

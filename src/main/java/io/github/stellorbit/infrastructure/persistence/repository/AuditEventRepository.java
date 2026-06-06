package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.AuditEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

  List<AuditEventEntity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
      String resourceType, UUID resourceId);
}

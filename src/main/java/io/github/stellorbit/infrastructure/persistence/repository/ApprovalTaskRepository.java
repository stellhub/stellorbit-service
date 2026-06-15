package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ApprovalTaskEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTaskEntity, UUID> {

  List<ApprovalTaskEntity> findByApprovalIdOrderByCreatedAtAsc(UUID approvalId);

  Optional<ApprovalTaskEntity> findByApprovalIdAndApprover(UUID approvalId, String approver);
}

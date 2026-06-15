package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.ApprovalPolicyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicyEntity, UUID> {

  List<ApprovalPolicyEntity>
      findByInstanceSpaceIdAndResourceTypeAndPolicyStatusOrderByCreatedAtDesc(
          UUID instanceSpaceId, String resourceType, String policyStatus);
}

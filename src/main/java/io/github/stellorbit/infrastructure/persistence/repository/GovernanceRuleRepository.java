package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GovernanceRuleRepository
    extends JpaRepository<GovernanceRuleEntity, UUID>,
        JpaSpecificationExecutor<GovernanceRuleEntity> {

  List<GovernanceRuleEntity> findByRuleType(String ruleType);

  List<GovernanceRuleEntity> findByInstanceSpaceIdAndApplicationId(
      UUID instanceSpaceId, UUID applicationId);

  List<GovernanceRuleEntity>
      findByInstanceSpaceIdAndApplicationIdAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
          UUID instanceSpaceId, UUID applicationId);

  List<GovernanceRuleEntity>
      findByInstanceSpaceIdAndApplicationIdAndIdInAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
          UUID instanceSpaceId, UUID applicationId, Collection<UUID> ids);
}

package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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

  /** 查询所有已启用的分布式限流规则，排除只能由客户端或网关本地执行的LOCAL限流规则。 */
  @Query(
      value =
          """
          SELECT g.*
          FROM stellorbit.governance_rules g
          JOIN stellorbit.rate_limit_rules r ON r.governance_rule_id = g.id
          WHERE g.rule_type = 'RATE_LIMIT'
            AND g.enabled = true
            AND (
              r.coordination_mode IN ('GLOBAL_SYNC', 'GLOBAL_QUOTA')
              OR r.enforcement_mode IN ('GLOBAL_SYNC', 'GLOBAL_QUOTA')
            )
          ORDER BY g.instance_space_id ASC, g.application_id ASC, g.priority ASC, g.rule_code ASC
          """,
      nativeQuery = true)
  List<GovernanceRuleEntity> findEnabledDistributedRateLimitRules();
}

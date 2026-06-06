package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceRuleService extends CrudService<GovernanceRuleEntity> {

  private final GovernanceRuleRepository repository;

  public GovernanceRuleService(GovernanceRuleRepository repository) {
    super(repository, "GovernanceRule");
    this.repository = repository;
  }

  /** 按实例空间和应用查询规则。 */
  @Transactional(readOnly = true)
  public List<GovernanceRuleEntity> findByScope(UUID instanceSpaceId, UUID applicationId) {
    return repository.findByInstanceSpaceIdAndApplicationId(instanceSpaceId, applicationId);
  }
}

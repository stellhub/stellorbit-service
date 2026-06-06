package io.github.stellorbit.application.port;

import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;

public interface GovernanceRuleContentCompiler {

  /** 编译治理规则为stellnula-service可发布的JSON内容。 */
  CompiledGovernanceRule compile(GovernanceRuleEntity rule, ApplicationEntity application);
}

package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RuleValidationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleValidationRepository extends JpaRepository<RuleValidationEntity, UUID> {

  List<RuleValidationEntity> findByRuleIdOrderByValidatedAtDesc(UUID ruleId);

  Page<RuleValidationEntity> findByRuleIdOrderByValidatedAtDesc(UUID ruleId, Pageable pageable);
}

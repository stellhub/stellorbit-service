package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.BreakerRuleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakerRuleRepository extends JpaRepository<BreakerRuleEntity, UUID> {}

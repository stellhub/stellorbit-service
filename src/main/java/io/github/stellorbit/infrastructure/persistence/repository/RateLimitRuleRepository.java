package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRuleEntity, UUID> {}

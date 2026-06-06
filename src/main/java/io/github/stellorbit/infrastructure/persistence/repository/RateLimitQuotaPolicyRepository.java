package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaPolicyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitQuotaPolicyRepository
    extends JpaRepository<RateLimitQuotaPolicyEntity, UUID> {

  boolean existsByRateLimitRuleId(UUID rateLimitRuleId);

  boolean existsByRateLimitRuleIdAndIdNot(UUID rateLimitRuleId, UUID id);

  Optional<RateLimitQuotaPolicyEntity> findByRateLimitRuleId(UUID rateLimitRuleId);
}

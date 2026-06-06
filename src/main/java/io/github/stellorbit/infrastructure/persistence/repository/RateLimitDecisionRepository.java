package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitDecisionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitDecisionRepository extends JpaRepository<RateLimitDecisionEntity, UUID> {}

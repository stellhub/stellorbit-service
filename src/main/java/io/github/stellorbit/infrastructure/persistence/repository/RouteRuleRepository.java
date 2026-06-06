package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RouteRuleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRuleRepository extends JpaRepository<RouteRuleEntity, UUID> {}

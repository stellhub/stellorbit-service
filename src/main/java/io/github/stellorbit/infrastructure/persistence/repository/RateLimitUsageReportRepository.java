package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitUsageReportEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitUsageReportRepository
    extends JpaRepository<RateLimitUsageReportEntity, UUID> {}

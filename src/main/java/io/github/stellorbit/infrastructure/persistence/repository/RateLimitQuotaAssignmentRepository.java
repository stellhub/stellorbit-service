package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaAssignmentEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitQuotaAssignmentRepository
    extends JpaRepository<RateLimitQuotaAssignmentEntity, UUID> {

  Optional<RateLimitQuotaAssignmentEntity>
      findTopByRateLimitRuleIdAndClientIdAndLimitKeyHashAndLeaseStatusAndExpiresAtAfterOrderByLeaseVersionDesc(
          UUID rateLimitRuleId,
          String clientId,
          String limitKeyHash,
          String leaseStatus,
          OffsetDateTime expiresAt);

  Optional<RateLimitQuotaAssignmentEntity>
      findTopByRateLimitRuleIdAndClientIdAndLimitKeyHashOrderByLeaseVersionDesc(
          UUID rateLimitRuleId, String clientId, String limitKeyHash);

  List<RateLimitQuotaAssignmentEntity> findByRateLimitRuleIdAndClientIdAndLeaseStatus(
      UUID rateLimitRuleId, String clientId, String leaseStatus);

  List<RateLimitQuotaAssignmentEntity> findByLeaseStatusAndExpiresAtBefore(
      String leaseStatus, OffsetDateTime expiresAt);
}

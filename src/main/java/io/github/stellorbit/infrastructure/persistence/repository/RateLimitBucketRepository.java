package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitBucketEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucketEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RateLimitBucketEntity> findByRateLimitRuleIdAndReleaseIdAndLimitKeyHashAndWindowStartAt(
      UUID rateLimitRuleId, UUID releaseId, String limitKeyHash, OffsetDateTime windowStartAt);
}

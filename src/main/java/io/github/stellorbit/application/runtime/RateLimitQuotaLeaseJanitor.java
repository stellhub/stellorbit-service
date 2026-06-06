package io.github.stellorbit.application.runtime;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaAssignmentEntity;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaAssignmentRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RateLimitQuotaLeaseJanitor {

  private final RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository;

  public RateLimitQuotaLeaseJanitor(
      RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository) {
    this.rateLimitQuotaAssignmentRepository = rateLimitQuotaAssignmentRepository;
  }

  /** 定时将过期配额租约标记为EXPIRED。 */
  @Scheduled(fixedDelayString = "${stellorbit.runtime.quota-lease-cleanup-fixed-delay-millis:5000}")
  @Transactional
  public void expireLeases() {
    List<RateLimitQuotaAssignmentEntity> expired =
        rateLimitQuotaAssignmentRepository.findByLeaseStatusAndExpiresAtBefore(
            "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC));
    for (RateLimitQuotaAssignmentEntity assignment : expired) {
      assignment.setLeaseStatus("EXPIRED");
    }
    rateLimitQuotaAssignmentRepository.saveAll(expired);
  }
}

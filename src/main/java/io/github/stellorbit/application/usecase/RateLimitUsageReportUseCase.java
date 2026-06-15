package io.github.stellorbit.application.usecase;

import io.github.stellorbit.application.runtime.RateLimitHashing;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitUsageReportEntity;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaAssignmentRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitUsageReportRepository;
import io.github.stellorbit.api.dto.RateLimitUsageReportRequest;
import io.github.stellorbit.api.dto.RateLimitUsageReportResponse;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitUsageReportUseCase {

  private final RateLimitUsageReportRepository rateLimitUsageReportRepository;
  private final RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository;

  public RateLimitUsageReportUseCase(
      RateLimitUsageReportRepository rateLimitUsageReportRepository,
      RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository) {
    this.rateLimitUsageReportRepository = rateLimitUsageReportRepository;
    this.rateLimitQuotaAssignmentRepository = rateLimitQuotaAssignmentRepository;
  }

  /** 接收客户端周期用量上报。 */
  @Transactional
  public RateLimitUsageReportResponse report(RateLimitUsageReportRequest request) {
    if (!request.reportWindowEnd().isAfter(request.reportWindowStart())) {
      throw new InvalidRuleRequestException("上报窗口结束时间必须晚于开始时间");
    }
    String limitKeyHash = RateLimitHashing.sha256(request.limitKey());
    RateLimitUsageReportEntity report = new RateLimitUsageReportEntity();
    report.setAssignmentId(request.assignmentId());
    report.setRateLimitRuleId(request.rateLimitRuleId());
    report.setReleaseId(request.releaseId());
    report.setClientId(request.clientId());
    report.setLimitKeyHash(limitKeyHash);
    report.setReportedUsed(defaultLong(request.reportedUsed()));
    report.setReportedAllowed(defaultLong(request.reportedAllowed()));
    report.setReportedRejected(defaultLong(request.reportedRejected()));
    report.setModelUsage(
        request.modelUsage() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(request.modelUsage()));
    report.setReportWindowStart(request.reportWindowStart());
    report.setReportWindowEnd(request.reportWindowEnd());
    report.setReportedAt(OffsetDateTime.now(ZoneOffset.UTC));
    RateLimitUsageReportEntity saved = rateLimitUsageReportRepository.saveAndFlush(report);
    updateAssignmentUsage(request, saved);
    return toResponse(saved);
  }

  private void updateAssignmentUsage(
      RateLimitUsageReportRequest request, RateLimitUsageReportEntity report) {
    if (request.assignmentId() == null) {
      return;
    }
    rateLimitQuotaAssignmentRepository
        .findById(request.assignmentId())
        .ifPresent(
            assignment -> {
              long used = defaultLong(assignment.getUsedQuota()) + report.getReportedUsed();
              assignment.setUsedQuota(used);
              assignment.setRemainingQuota(
                  Math.max(defaultLong(assignment.getAssignedQuota()) - used, 0L));
              rateLimitQuotaAssignmentRepository.save(assignment);
            });
  }

  private long defaultLong(Long value) {
    return value == null ? 0L : value;
  }

  private RateLimitUsageReportResponse toResponse(RateLimitUsageReportEntity report) {
    return new RateLimitUsageReportResponse(
        report.getId(),
        report.getAssignmentId(),
        report.getRateLimitRuleId(),
        report.getReleaseId(),
        report.getClientId(),
        report.getLimitKeyHash(),
        report.getReportedUsed(),
        report.getReportedAllowed(),
        report.getReportedRejected(),
        report.getReportedAt());
  }
}

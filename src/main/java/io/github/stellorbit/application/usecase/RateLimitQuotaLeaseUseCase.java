package io.github.stellorbit.application.usecase;

import io.github.stellorbit.application.runtime.RateLimitHashing;
import io.github.stellorbit.application.runtime.RateLimitRuntimeRule;
import io.github.stellorbit.application.runtime.RuleRuntimeCache;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaAssignmentEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaPolicyEntity;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaAssignmentRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaPolicyRepository;
import io.github.stellorbit.api.dto.RateLimitQuotaLeaseRequest;
import io.github.stellorbit.api.dto.RateLimitQuotaLeaseResponse;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitQuotaLeaseUseCase {

  private final RuleRuntimeCache ruleRuntimeCache;
  private final RateLimitQuotaPolicyRepository rateLimitQuotaPolicyRepository;
  private final RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository;

  public RateLimitQuotaLeaseUseCase(
      RuleRuntimeCache ruleRuntimeCache,
      RateLimitQuotaPolicyRepository rateLimitQuotaPolicyRepository,
      RateLimitQuotaAssignmentRepository rateLimitQuotaAssignmentRepository) {
    this.ruleRuntimeCache = ruleRuntimeCache;
    this.rateLimitQuotaPolicyRepository = rateLimitQuotaPolicyRepository;
    this.rateLimitQuotaAssignmentRepository = rateLimitQuotaAssignmentRepository;
  }

  /** 为客户端分配或复用本地配额租约。 */
  @Transactional
  public RateLimitQuotaLeaseResponse lease(RateLimitQuotaLeaseRequest request) {
    RateLimitRuntimeRule runtimeRule =
        ruleRuntimeCache
            .findRateLimitRule(request.rateLimitRuleId())
            .orElseThrow(() -> new InvalidRuleRequestException("限流规则未加载到运行时缓存"));
    RateLimitQuotaPolicyEntity policy =
        rateLimitQuotaPolicyRepository
            .findByRateLimitRuleId(request.rateLimitRuleId())
            .orElseThrow(() -> new InvalidRuleRequestException("限流规则未配置配额策略"));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String limitKeyHash = RateLimitHashing.sha256(request.limitKey());
    RateLimitQuotaAssignmentEntity active =
        rateLimitQuotaAssignmentRepository
            .findTopByRateLimitRuleIdAndClientIdAndLimitKeyHashAndLeaseStatusAndExpiresAtAfterOrderByLeaseVersionDesc(
                request.rateLimitRuleId(), request.clientId(), limitKeyHash, "ACTIVE", now)
            .orElse(null);
    if (active != null) {
      return toResponse(active);
    }
    RateLimitQuotaAssignmentEntity previous =
        rateLimitQuotaAssignmentRepository
            .findTopByRateLimitRuleIdAndClientIdAndLimitKeyHashOrderByLeaseVersionDesc(
                request.rateLimitRuleId(), request.clientId(), limitKeyHash)
            .orElse(null);
    RateLimitQuotaAssignmentEntity assignment = new RateLimitQuotaAssignmentEntity();
    assignment.setRateLimitRuleId(request.rateLimitRuleId());
    assignment.setQuotaPolicyId(policy.getId());
    assignment.setReleaseId(runtimeRule.releaseId());
    assignment.setClientId(request.clientId());
    assignment.setLimitKeyHash(limitKeyHash);
    assignment.setAssignedQuota(assignedQuota(runtimeRule, policy));
    assignment.setUsedQuota(0L);
    assignment.setRemainingQuota(assignment.getAssignedQuota());
    assignment.setLeaseVersion(previous == null ? 1L : previous.getLeaseVersion() + 1);
    assignment.setLeaseStatus("ACTIVE");
    assignment.setAssignedAt(now);
    assignment.setExpiresAt(now.plusNanos(policy.getAssignmentTtlMillis() * 1_000_000));
    return toResponse(rateLimitQuotaAssignmentRepository.saveAndFlush(assignment));
  }

  private long assignedQuota(RateLimitRuntimeRule runtimeRule, RateLimitQuotaPolicyEntity policy) {
    long quota = runtimeRule.quota();
    if (policy.getMaxAssignmentQuota() != null) {
      quota = Math.min(quota, policy.getMaxAssignmentQuota());
    }
    if (policy.getMinAssignmentQuota() != null) {
      quota = Math.max(quota, policy.getMinAssignmentQuota());
    }
    return Math.max(quota, 0L);
  }

  private RateLimitQuotaLeaseResponse toResponse(RateLimitQuotaAssignmentEntity assignment) {
    return new RateLimitQuotaLeaseResponse(
        assignment.getId(),
        assignment.getRateLimitRuleId(),
        assignment.getReleaseId(),
        assignment.getClientId(),
        assignment.getLimitKeyHash(),
        assignment.getAssignedQuota(),
        assignment.getRemainingQuota(),
        assignment.getLeaseVersion(),
        assignment.getLeaseStatus(),
        assignment.getAssignedAt(),
        assignment.getExpiresAt());
  }
}

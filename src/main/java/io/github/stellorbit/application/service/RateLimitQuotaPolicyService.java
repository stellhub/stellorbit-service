package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaPolicyEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaPolicyRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import io.github.stellorbit.interfaces.http.error.InvalidRuleRequestException;
import io.github.stellorbit.interfaces.http.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitQuotaPolicyService extends CrudService<RateLimitQuotaPolicyEntity> {

  private static final Set<String> ALLOCATION_ALGORITHMS =
      Set.of(
          "EQUAL_SPLIT", "WEIGHTED_SPLIT", "DEMAND_AWARE", "HOTSPOT_AWARE", "BORROWING", "MANUAL");
  private static final Set<String> FAILOVER_STRATEGIES =
      Set.of("FAIL_OPEN", "FAIL_CLOSED", "LOCAL_FALLBACK", "LAST_ASSIGNMENT");

  private final RateLimitQuotaPolicyRepository repository;
  private final GovernanceRuleRepository governanceRuleRepository;
  private final RateLimitRuleRepository rateLimitRuleRepository;

  public RateLimitQuotaPolicyService(
      RateLimitQuotaPolicyRepository repository,
      GovernanceRuleRepository governanceRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository) {
    super(repository, "RateLimitQuotaPolicy");
    this.repository = repository;
    this.governanceRuleRepository = governanceRuleRepository;
    this.rateLimitRuleRepository = rateLimitRuleRepository;
  }

  /** 创建限流配额策略。 */
  @Override
  @Transactional
  public RateLimitQuotaPolicyEntity create(RateLimitQuotaPolicyEntity entity) {
    validateQuotaPolicy(entity, null);
    entity.setId(null);
    return repository.save(entity);
  }

  /** 更新限流配额策略。 */
  @Override
  @Transactional
  public RateLimitQuotaPolicyEntity update(UUID id, RateLimitQuotaPolicyEntity entity) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("RateLimitQuotaPolicy", id);
    }
    validateQuotaPolicy(entity, id);
    entity.setId(id);
    return repository.save(entity);
  }

  private void validateQuotaPolicy(RateLimitQuotaPolicyEntity entity, UUID currentId) {
    validateBoundRateLimitRule(entity.getRateLimitRuleId());
    validateUniquePolicy(entity.getRateLimitRuleId(), currentId);
    validatePositive("配额租约TTL", entity.getAssignmentTtlMillis());
    validatePositive("用量上报间隔", entity.getReportIntervalMillis());
    validatePositive("配额重平衡间隔", entity.getRebalanceIntervalMillis());
    validateReportAndRebalanceIntervals(entity);
    validateNonNegative("最大透支比例", entity.getMaxOverdraftRatio());
    validatePositive("热点分片数量", entity.getHotspotShardCount());
    validateNonNegative("最小分配配额", entity.getMinAssignmentQuota());
    validateMaxAssignmentQuota(entity);
    validateEnum("配额分配算法", entity.getAllocationAlgorithm(), ALLOCATION_ALGORITHMS);
    validateEnum("故障策略", entity.getFailoverStrategy(), FAILOVER_STRATEGIES);
    validateAlgorithmConfig(entity);
  }

  private void validateBoundRateLimitRule(UUID rateLimitRuleId) {
    GovernanceRuleEntity governanceRule =
        governanceRuleRepository
            .findById(rateLimitRuleId)
            .orElseThrow(() -> new ResourceNotFoundException("RateLimitRule", rateLimitRuleId));
    if (!"RATE_LIMIT".equals(governanceRule.getRuleType())) {
      throw new InvalidRuleRequestException("配额策略只能绑定到RATE_LIMIT类型规则");
    }

    RateLimitRuleEntity rateLimitRule =
        rateLimitRuleRepository
            .findById(rateLimitRuleId)
            .orElseThrow(() -> new ResourceNotFoundException("RateLimitRule", rateLimitRuleId));
    if (!"GLOBAL_QUOTA".equals(rateLimitRule.getEnforcementMode())) {
      throw new InvalidRuleRequestException("限流规则的enforcementMode必须是GLOBAL_QUOTA");
    }
  }

  private void validateUniquePolicy(UUID rateLimitRuleId, UUID currentId) {
    boolean exists =
        currentId == null
            ? repository.existsByRateLimitRuleId(rateLimitRuleId)
            : repository.existsByRateLimitRuleIdAndIdNot(rateLimitRuleId, currentId);
    if (exists) {
      throw new InvalidRuleRequestException("该限流规则已存在配额策略");
    }
  }

  private void validateReportAndRebalanceIntervals(RateLimitQuotaPolicyEntity entity) {
    if (entity.getReportIntervalMillis() != null
        && entity.getAssignmentTtlMillis() != null
        && entity.getReportIntervalMillis() > entity.getAssignmentTtlMillis()) {
      throw new InvalidRuleRequestException("用量上报间隔不能大于配额租约TTL");
    }
    if (entity.getReportIntervalMillis() != null
        && entity.getRebalanceIntervalMillis() != null
        && entity.getReportIntervalMillis() > entity.getRebalanceIntervalMillis()) {
      throw new InvalidRuleRequestException("用量上报间隔不能大于配额重平衡间隔");
    }
  }

  private void validateMaxAssignmentQuota(RateLimitQuotaPolicyEntity entity) {
    if (entity.getMaxAssignmentQuota() == null) {
      return;
    }
    validateNonNegative("最大分配配额", entity.getMaxAssignmentQuota());
    if (entity.getMinAssignmentQuota() != null
        && entity.getMaxAssignmentQuota() < entity.getMinAssignmentQuota()) {
      throw new InvalidRuleRequestException("最大分配配额不能小于最小分配配额");
    }
  }

  private void validateAlgorithmConfig(RateLimitQuotaPolicyEntity entity) {
    String algorithm = entity.getAllocationAlgorithm();
    Map<String, Object> config = entity.getAlgorithmConfig();
    if ("WEIGHTED_SPLIT".equals(algorithm) && isEmptyConfig(config)) {
      throw new InvalidRuleRequestException("WEIGHTED_SPLIT算法必须提供algorithmConfig");
    }
    if ("MANUAL".equals(algorithm) && isEmptyConfig(config)) {
      throw new InvalidRuleRequestException("MANUAL算法必须提供algorithmConfig");
    }
    if ("HOTSPOT_AWARE".equals(algorithm)
        && (entity.getHotspotShardCount() == null || entity.getHotspotShardCount() <= 1)) {
      throw new InvalidRuleRequestException("HOTSPOT_AWARE算法的热点分片数量必须大于1");
    }
    if ("BORROWING".equals(algorithm)
        && (entity.getMaxOverdraftRatio() == null
            || entity.getMaxOverdraftRatio().compareTo(BigDecimal.ZERO) <= 0)) {
      throw new InvalidRuleRequestException("BORROWING算法的最大透支比例必须大于0");
    }
  }

  private boolean isEmptyConfig(Map<String, Object> config) {
    return config == null || config.isEmpty();
  }

  private void validateEnum(String name, String value, Set<String> supportedValues) {
    if (value == null || !supportedValues.contains(value)) {
      throw new InvalidRuleRequestException(name + "不合法");
    }
  }

  private void validatePositive(String name, Long value) {
    if (value == null || value <= 0) {
      throw new InvalidRuleRequestException(name + "必须大于0");
    }
  }

  private void validatePositive(String name, Integer value) {
    if (value == null || value <= 0) {
      throw new InvalidRuleRequestException(name + "必须大于0");
    }
  }

  private void validateNonNegative(String name, Long value) {
    if (value == null || value < 0) {
      throw new InvalidRuleRequestException(name + "不能小于0");
    }
  }

  private void validateNonNegative(String name, BigDecimal value) {
    if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidRuleRequestException(name + "不能小于0");
    }
  }
}

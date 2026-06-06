package io.github.stellorbit.application.runtime;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaPolicyEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitQuotaPolicyRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RuleRuntimeCache {

  private final RateLimitRuleRepository rateLimitRuleRepository;
  private final GovernanceRuleRepository governanceRuleRepository;
  private final RateLimitQuotaPolicyRepository rateLimitQuotaPolicyRepository;
  private volatile Map<UUID, RateLimitRuntimeRule> rateLimitRules = Map.of();
  private volatile OffsetDateTime lastLoadedAt;
  private volatile RuntimeRuleWatermark watermark = RuntimeRuleWatermark.empty();

  public RuleRuntimeCache(
      RateLimitRuleRepository rateLimitRuleRepository,
      GovernanceRuleRepository governanceRuleRepository,
      RateLimitQuotaPolicyRepository rateLimitQuotaPolicyRepository) {
    this.rateLimitRuleRepository = rateLimitRuleRepository;
    this.governanceRuleRepository = governanceRuleRepository;
    this.rateLimitQuotaPolicyRepository = rateLimitQuotaPolicyRepository;
  }

  /** 启动时加载限流规则运行时缓存。 */
  @PostConstruct
  public void loadOnStartup() {
    refreshRateLimitRules();
  }

  /** 秒级从DB刷新限流规则全量缓存。 */
  @Scheduled(fixedDelayString = "${stellorbit.runtime.rule-sync-fixed-delay-millis:1000}")
  @Transactional(readOnly = true)
  public void refreshRateLimitRules() {
    List<RateLimitRuleEntity> details = rateLimitRuleRepository.findAll();
    Map<UUID, GovernanceRuleEntity> rules =
        governanceRuleRepository.findAllById(ids(details)).stream()
            .collect(Collectors.toMap(GovernanceRuleEntity::getId, Function.identity()));
    Map<UUID, RateLimitQuotaPolicyEntity> quotaPolicies =
        rateLimitQuotaPolicyRepository.findAll().stream()
            .collect(
                Collectors.toMap(
                    RateLimitQuotaPolicyEntity::getRateLimitRuleId, Function.identity()));
    RuntimeRuleWatermark nextWatermark = watermark(details, rules, quotaPolicies);
    if (nextWatermark.equals(watermark)) {
      lastLoadedAt = OffsetDateTime.now();
      return;
    }
    Map<UUID, RateLimitRuntimeRule> loaded = new LinkedHashMap<>();
    OffsetDateTime loadedAt = OffsetDateTime.now();
    for (RateLimitRuleEntity detail : details) {
      GovernanceRuleEntity rule = rules.get(detail.getId());
      RateLimitQuotaPolicyEntity quotaPolicy = quotaPolicies.get(detail.getId());
      toRuntimeRule(rule, detail, quotaPolicy, loadedAt)
          .ifPresent(runtime -> loaded.put(runtime.rateLimitRuleId(), runtime));
    }
    rateLimitRules = Map.copyOf(loaded);
    lastLoadedAt = loadedAt;
    watermark = nextWatermark;
  }

  /** 按规则ID读取本地运行时限流规则。 */
  public Optional<RateLimitRuntimeRule> findRateLimitRule(UUID rateLimitRuleId) {
    return Optional.ofNullable(rateLimitRules.get(rateLimitRuleId));
  }

  /** 返回缓存中限流规则数量。 */
  public int rateLimitRuleCount() {
    return rateLimitRules.size();
  }

  public OffsetDateTime getLastLoadedAt() {
    return lastLoadedAt;
  }

  public RuntimeRuleWatermark getWatermark() {
    return watermark;
  }

  private Optional<RateLimitRuntimeRule> toRuntimeRule(
      GovernanceRuleEntity rule,
      RateLimitRuleEntity detail,
      RateLimitQuotaPolicyEntity quotaPolicy,
      OffsetDateTime loadedAt) {
    if (rule == null
        || !"RATE_LIMIT".equals(rule.getRuleType())
        || !Boolean.TRUE.equals(rule.getEnabled())
        || rule.getLatestReleaseId() == null) {
      return Optional.empty();
    }
    long quota =
        readPositiveLong(detail.getQuotaConfig(), "quota", "permits", "limit", "maxPermits");
    long windowMillis =
        detail.getWindowConfig().containsKey("windowSeconds")
            ? readPositiveLong(detail.getWindowConfig(), "windowSeconds") * 1000L
            : readPositiveLong(
                detail.getWindowConfig(), "windowMillis", "durationMillis", "intervalMillis");
    if (quota <= 0 || windowMillis <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        new RateLimitRuntimeRule(
            detail.getId(),
            rule.getInstanceSpaceId(),
            rule.getApplicationId(),
            rule.getLatestReleaseId(),
            rule.getRuleCode(),
            rule.getRuleName(),
            detail.getLimitAlgorithm(),
            detail.getEnforcementMode(),
            quota,
            windowMillis,
            hotspotShardCount(quotaPolicy),
            fallbackStrategy(detail),
            detail.getFallbackPolicy(),
            detail.getResponsePolicy(),
            detail.getModelLimitConfig(),
            maxTime(rule.getUpdatedAt(), detail.getUpdatedAt()),
            loadedAt));
  }

  private RuntimeRuleWatermark watermark(
      List<RateLimitRuleEntity> details,
      Map<UUID, GovernanceRuleEntity> rules,
      Map<UUID, RateLimitQuotaPolicyEntity> quotaPolicies) {
    OffsetDateTime maxUpdatedAt = null;
    long versionSum = 0L;
    for (RateLimitRuleEntity detail : details) {
      GovernanceRuleEntity rule = rules.get(detail.getId());
      if (rule == null) {
        continue;
      }
      maxUpdatedAt = maxTime(maxUpdatedAt, maxTime(rule.getUpdatedAt(), detail.getUpdatedAt()));
      versionSum += rule.getDraftVersion() == null ? 0L : rule.getDraftVersion();
      RateLimitQuotaPolicyEntity policy = quotaPolicies.get(detail.getId());
      if (policy != null) {
        maxUpdatedAt = maxTime(maxUpdatedAt, policy.getUpdatedAt());
      }
    }
    return new RuntimeRuleWatermark(details.size(), versionSum, maxUpdatedAt);
  }

  private OffsetDateTime maxTime(OffsetDateTime left, OffsetDateTime right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left.isAfter(right) ? left : right;
  }

  private int hotspotShardCount(RateLimitQuotaPolicyEntity quotaPolicy) {
    if (quotaPolicy == null || quotaPolicy.getHotspotShardCount() == null) {
      return 1;
    }
    return Math.max(quotaPolicy.getHotspotShardCount(), 1);
  }

  private String fallbackStrategy(RateLimitRuleEntity detail) {
    Object value =
        detail.getFallbackPolicy() == null ? null : detail.getFallbackPolicy().get("strategy");
    return value == null ? "FAIL_CLOSED" : value.toString();
  }

  private Collection<UUID> ids(List<RateLimitRuleEntity> details) {
    return details.stream().map(RateLimitRuleEntity::getId).toList();
  }

  private long readPositiveLong(Map<String, Object> config, String... keys) {
    if (config == null) {
      return -1L;
    }
    for (String key : keys) {
      Object value = config.get(key);
      if (value == null) {
        continue;
      }
      long number = toLong(value);
      if (number > 0) {
        return number;
      }
    }
    return -1L;
  }

  private long toLong(Object value) {
    try {
      return switch (value) {
        case Number numeric -> numeric.longValue();
        case String text when !text.isBlank() -> Long.parseLong(text);
        default -> -1L;
      };
    } catch (NumberFormatException exception) {
      return -1L;
    }
  }

  public record RuntimeRuleWatermark(
      long ruleCount, long draftVersionSum, OffsetDateTime maxUpdatedAt) {
    public static RuntimeRuleWatermark empty() {
      return new RuntimeRuleWatermark(0L, 0L, null);
    }
  }
}

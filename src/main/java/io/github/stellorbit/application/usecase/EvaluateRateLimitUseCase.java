package io.github.stellorbit.application.usecase;

import io.github.stellorbit.application.runtime.LocalRateLimitCounterStore;
import io.github.stellorbit.application.runtime.LocalRateLimitDecision;
import io.github.stellorbit.application.runtime.RateLimitDecisionBuffer;
import io.github.stellorbit.application.runtime.RateLimitHashing;
import io.github.stellorbit.application.runtime.RateLimitRuntimeMetrics;
import io.github.stellorbit.application.runtime.RateLimitRuntimeRule;
import io.github.stellorbit.application.runtime.RuleRuntimeCache;
import io.github.stellorbit.interfaces.http.dto.EvaluateRateLimitRequest;
import io.github.stellorbit.interfaces.http.dto.EvaluateRateLimitResponse;
import io.github.stellorbit.interfaces.http.error.InvalidRuleRequestException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvaluateRateLimitUseCase {

  private static final long DEFAULT_REQUESTED_PERMITS = 1L;

  private final RuleRuntimeCache ruleRuntimeCache;
  private final LocalRateLimitCounterStore localRateLimitCounterStore;
  private final RateLimitRuntimeMetrics metrics;
  private final RateLimitDecisionBuffer decisionBuffer;

  public EvaluateRateLimitUseCase(
      RuleRuntimeCache ruleRuntimeCache,
      LocalRateLimitCounterStore localRateLimitCounterStore,
      RateLimitRuntimeMetrics metrics,
      RateLimitDecisionBuffer decisionBuffer) {
    this.ruleRuntimeCache = ruleRuntimeCache;
    this.localRateLimitCounterStore = localRateLimitCounterStore;
    this.metrics = metrics;
    this.decisionBuffer = decisionBuffer;
  }

  /** 使用本地全量内存规则和本地计数器执行高性能限流判定。 */
  public EvaluateRateLimitResponse evaluate(EvaluateRateLimitRequest request) {
    RateLimitRuntimeRule runtimeRule =
        ruleRuntimeCache
            .findRateLimitRule(request.rateLimitRuleId())
            .orElseThrow(() -> new InvalidRuleRequestException("限流规则未发布、未启用或尚未加载到本地缓存"));
    if (request.releaseId() != null && !request.releaseId().equals(runtimeRule.releaseId())) {
      throw new InvalidRuleRequestException("请求发布版本与本地运行时缓存版本不一致");
    }
    long requestedPermits =
        effectivePermits(runtimeRule, request.requestedPermits(), request.modelRequestUnits());
    if (requestedPermits <= 0) {
      throw new InvalidRuleRequestException("请求配额必须大于0");
    }
    String limitKeyHash = RateLimitHashing.sha256(request.limitKey());
    LocalRateLimitDecision decision =
        evaluateWithFallback(runtimeRule, request, limitKeyHash, requestedPermits);
    metrics.recordDecision(decision);
    decisionBuffer.sample(
        runtimeRule,
        decision,
        request.requestId(),
        request.clientId(),
        request.modelRequestUnits());
    return toResponse(runtimeRule, decision);
  }

  private LocalRateLimitDecision evaluateWithFallback(
      RateLimitRuntimeRule runtimeRule,
      EvaluateRateLimitRequest request,
      String limitKeyHash,
      long requestedPermits) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    try {
      return localRateLimitCounterStore.evaluate(
          runtimeRule,
          limitKeyHash,
          request.requestId() == null ? request.clientId() : request.requestId(),
          requestedPermits,
          now);
    } catch (RuntimeException exception) {
      if ("FAIL_OPEN".equalsIgnoreCase(runtimeRule.fallbackStrategy())) {
        return new LocalRateLimitDecision(
            UUID.randomUUID(),
            null,
            limitKeyHash,
            limitKeyHash,
            null,
            runtimeRule.limitAlgorithm(),
            now,
            now.plusSeconds(1),
            runtimeRule.quota(),
            requestedPermits,
            0L,
            runtimeRule.quota(),
            true,
            null,
            "FALLBACK_FAIL_OPEN: " + exception.getMessage(),
            true,
            now.plusSeconds(1));
      }
      throw exception;
    }
  }

  private long effectivePermits(
      RateLimitRuntimeRule runtimeRule,
      Long requestedPermits,
      Map<String, Object> modelRequestUnits) {
    long basePermits = requestedPermits == null ? DEFAULT_REQUESTED_PERMITS : requestedPermits;
    if (modelRequestUnits == null || modelRequestUnits.isEmpty()) {
      return basePermits;
    }
    String metric = configText(runtimeRule.modelLimitConfig(), "quotaMetric", "REQUESTS");
    long modelPermits =
        switch (metric) {
          case "INPUT_TOKENS" -> number(modelRequestUnits.get("inputTokens"));
          case "OUTPUT_TOKENS" -> number(modelRequestUnits.get("outputTokens"));
          case "TOTAL_TOKENS" ->
              Math.max(
                  number(modelRequestUnits.get("totalTokens")),
                  number(modelRequestUnits.get("inputTokens"))
                      + number(modelRequestUnits.get("outputTokens")));
          case "COST" ->
              Math.round(
                  decimal(modelRequestUnits.get("cost"))
                      * number(modelConfig(runtimeRule).getOrDefault("costScale", 10000)));
          default -> basePermits;
        };
    return Math.max(basePermits, modelPermits);
  }

  private EvaluateRateLimitResponse toResponse(
      RateLimitRuntimeRule runtimeRule, LocalRateLimitDecision decision) {
    return new EvaluateRateLimitResponse(
        decision.decisionId(),
        decision.bucketId(),
        runtimeRule.rateLimitRuleId(),
        runtimeRule.releaseId(),
        decision.limitKeyHash(),
        decision.originalLimitKeyHash(),
        decision.hotspotShard(),
        decision.windowStrategy(),
        decision.windowStartAt(),
        decision.windowEndAt(),
        decision.quota(),
        decision.requestedPermits(),
        decision.usedPermits(),
        decision.remainingPermits(),
        decision.allowed(),
        decision.retryAfterMillis(),
        decision.decisionReason(),
        decision.fallbackUsed(),
        decision.resetAt());
  }

  private String configText(Map<String, Object> config, String key, String defaultValue) {
    Object value = config == null ? null : config.get(key);
    return value == null ? defaultValue : value.toString();
  }

  private Map<String, Object> modelConfig(RateLimitRuntimeRule runtimeRule) {
    return runtimeRule.modelLimitConfig() == null ? Map.of() : runtimeRule.modelLimitConfig();
  }

  private long number(Object value) {
    try {
      return switch (value) {
        case Number number -> number.longValue();
        case String text when !text.isBlank() -> Long.parseLong(text);
        default -> 0L;
      };
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }

  private double decimal(Object value) {
    try {
      return switch (value) {
        case Number number -> number.doubleValue();
        case String text when !text.isBlank() -> Double.parseDouble(text);
        default -> 0D;
      };
    } catch (NumberFormatException exception) {
      return 0D;
    }
  }
}

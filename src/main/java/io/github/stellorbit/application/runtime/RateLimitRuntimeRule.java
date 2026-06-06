package io.github.stellorbit.application.runtime;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RateLimitRuntimeRule(
    UUID rateLimitRuleId,
    UUID instanceSpaceId,
    UUID applicationId,
    UUID releaseId,
    String ruleCode,
    String ruleName,
    String limitAlgorithm,
    String enforcementMode,
    long quota,
    long windowMillis,
    int hotspotShardCount,
    String fallbackStrategy,
    Map<String, Object> fallbackPolicy,
    Map<String, Object> responsePolicy,
    Map<String, Object> modelLimitConfig,
    OffsetDateTime ruleUpdatedAt,
    OffsetDateTime loadedAt) {}

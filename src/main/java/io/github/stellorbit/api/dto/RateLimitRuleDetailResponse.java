package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RateLimitRuleDetailResponse(
    UUID id,
    String limitMode,
    String limitType,
    String limitAlgorithm,
    String trafficProtocol,
    String executionLocation,
    String coordinationMode,
    String enforcementMode,
    Map<String, Object> targetSelector,
    Map<String, Object> requestMatcher,
    Map<String, Object> keyExtractor,
    List<Object> dimensions,
    Map<String, Object> quotaConfig,
    Map<String, Object> windowConfig,
    Map<String, Object> burstConfig,
    Map<String, Object> concurrencyConfig,
    Map<String, Object> hotspotConfig,
    Map<String, Object> customPolicy,
    Map<String, Object> modelLimitConfig,
    Map<String, Object> fallbackPolicy,
    Map<String, Object> responsePolicy,
    Map<String, Object> observabilityConfig,
    Map<String, Object> shadowConfig,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

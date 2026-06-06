package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RateLimitRuleDetailResponse(
    UUID id,
    String limitType,
    String limitAlgorithm,
    String enforcementMode,
    Map<String, Object> targetSelector,
    List<Object> dimensions,
    Map<String, Object> quotaConfig,
    Map<String, Object> windowConfig,
    Map<String, Object> burstConfig,
    Map<String, Object> modelLimitConfig,
    Map<String, Object> fallbackPolicy,
    Map<String, Object> responsePolicy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

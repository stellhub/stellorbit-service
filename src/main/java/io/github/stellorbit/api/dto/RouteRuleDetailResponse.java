package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RouteRuleDetailResponse(
    UUID id,
    String routeType,
    String trafficDirection,
    String protocol,
    List<Object> gateways,
    List<Object> hosts,
    Map<String, Object> sourceSelector,
    List<Object> matchConditions,
    List<Object> destinations,
    Map<String, Object> routeAction,
    Map<String, Object> rewritePolicy,
    Map<String, Object> redirectPolicy,
    Map<String, Object> mirrorPolicy,
    Map<String, Object> faultInjectionPolicy,
    Map<String, Object> timeoutPolicy,
    Map<String, Object> retryPolicy,
    Map<String, Object> loadBalancePolicy,
    Map<String, Object> localityPolicy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

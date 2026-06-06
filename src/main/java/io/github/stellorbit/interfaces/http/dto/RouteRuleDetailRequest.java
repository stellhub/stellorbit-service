package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RouteRuleDetailRequest(
    UUID id,
    @NotBlank(message = "路由类型不能为空") String routeType,
    String trafficDirection,
    @NotBlank(message = "协议不能为空") String protocol,
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
    Map<String, Object> localityPolicy)
    implements RuleDetailMutationRequest {}

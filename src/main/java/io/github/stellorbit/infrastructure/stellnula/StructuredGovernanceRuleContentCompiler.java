package io.github.stellorbit.infrastructure.stellnula;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.infrastructure.cue.CueCompilationResult;
import io.github.stellorbit.infrastructure.cue.CueRuleCompiler;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.AuthPolicyRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.BreakerRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RouteRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.AuthPolicyRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.BreakerRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RouteRuleRepository;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class StructuredGovernanceRuleContentCompiler implements GovernanceRuleContentCompiler {

  private final RouteRuleRepository routeRuleRepository;
  private final BreakerRuleRepository breakerRuleRepository;
  private final RateLimitRuleRepository rateLimitRuleRepository;
  private final AuthPolicyRuleRepository authPolicyRuleRepository;
  private final CueRuleCompiler cueRuleCompiler;
  private final ObjectMapper objectMapper;
  private final ObjectMapper canonicalObjectMapper;

  public StructuredGovernanceRuleContentCompiler(
      RouteRuleRepository routeRuleRepository,
      BreakerRuleRepository breakerRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository,
      AuthPolicyRuleRepository authPolicyRuleRepository,
      CueRuleCompiler cueRuleCompiler,
      ObjectMapper objectMapper) {
    this.routeRuleRepository = routeRuleRepository;
    this.breakerRuleRepository = breakerRuleRepository;
    this.rateLimitRuleRepository = rateLimitRuleRepository;
    this.authPolicyRuleRepository = authPolicyRuleRepository;
    this.cueRuleCompiler = cueRuleCompiler;
    this.objectMapper = objectMapper;
    this.canonicalObjectMapper =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  /** 将结构化规则编译为stellnula治理规则JSON。 */
  @Override
  public CompiledGovernanceRule compile(GovernanceRuleEntity rule, ApplicationEntity application) {
    String stellnulaRuleType = toStellnulaRuleType(rule.getRuleType());
    Map<String, Object> contentModel = new LinkedHashMap<>();
    contentModel.put("ruleType", stellnulaRuleType);
    contentModel.put("targetService", targetService(rule, application));
    String status = Boolean.TRUE.equals(rule.getEnabled()) ? "ACTIVE" : "DISABLED";
    Integer priority = defaultPriority(rule.getPriority());
    contentModel.put("schemaVersion", "stellorbit.governance.v1");
    contentModel.put("status", status);
    contentModel.put("priority", priority);
    contentModel.put("ruleCode", rule.getRuleCode());
    contentModel.put("ruleName", rule.getRuleName());
    contentModel.put("draftVersion", rule.getDraftVersion());
    contentModel.put("sourceFormat", rule.getSourceFormat());
    contentModel.put("runtimeFormat", rule.getRuntimeFormat());
    contentModel.put("enabled", rule.getEnabled());
    putRuleSpecificContent(rule, contentModel);

    CueCompilationResult cueResult = cueRuleCompiler.compile(rule, contentModel);
    Map<String, Object> normalizedModel = cueResult.normalizedModel();
    String content = toCanonicalJson(normalizedModel);
    return new CompiledGovernanceRule(
        rule,
        configId(application, rule, stellnulaRuleType),
        stellnulaRuleType,
        (String) normalizedModel.get("targetService"),
        (String) normalizedModel.get("status"),
        (Integer) normalizedModel.get("priority"),
        cueResult.schemaVersion(),
        content,
        sha256(content),
        normalizedModel,
        cueResult.warnings(),
        cueResult.explain());
  }

  private void putRuleSpecificContent(GovernanceRuleEntity rule, Map<String, Object> contentModel) {
    switch (rule.getRuleType()) {
      case "ROUTE" -> contentModel.put("routes", routeContent(rule));
      case "BREAKER" -> contentModel.put("breaker", breakerContent(rule));
      case "RATE_LIMIT" -> contentModel.put("limit", rateLimitContent(rule));
      case "AUTH" -> contentModel.put("auth", authContent(rule));
      default -> throw new InvalidRuleRequestException("不支持的规则类型: " + rule.getRuleType());
    }
  }

  private Map<String, Object> routeContent(GovernanceRuleEntity rule) {
    RouteRuleEntity detail =
        routeRuleRepository
            .findById(rule.getId())
            .orElseThrow(() -> new InvalidRuleRequestException("路由规则明细不存在"));
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("routeType", detail.getRouteType());
    content.put("trafficDirection", detail.getTrafficDirection());
    content.put("protocol", detail.getProtocol());
    content.put("gateways", detail.getGateways());
    content.put("hosts", detail.getHosts());
    content.put("sourceSelector", detail.getSourceSelector());
    content.put("matchConditions", detail.getMatchConditions());
    content.put("destinations", detail.getDestinations());
    content.put("routeAction", detail.getRouteAction());
    content.put("rewritePolicy", detail.getRewritePolicy());
    content.put("redirectPolicy", detail.getRedirectPolicy());
    content.put("mirrorPolicy", detail.getMirrorPolicy());
    content.put("faultInjectionPolicy", detail.getFaultInjectionPolicy());
    content.put("timeoutPolicy", detail.getTimeoutPolicy());
    content.put("retryPolicy", detail.getRetryPolicy());
    content.put("loadBalancePolicy", detail.getLoadBalancePolicy());
    content.put("localityPolicy", detail.getLocalityPolicy());
    return content;
  }

  private Map<String, Object> breakerContent(GovernanceRuleEntity rule) {
    BreakerRuleEntity detail =
        breakerRuleRepository
            .findById(rule.getId())
            .orElseThrow(() -> new InvalidRuleRequestException("熔断规则明细不存在"));
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("breakerType", detail.getBreakerType());
    content.put("protocol", detail.getProtocol());
    content.put("targetSelector", detail.getTargetSelector());
    content.put("windowType", detail.getWindowType());
    content.put("windowSize", detail.getWindowSize());
    content.put("minimumCalls", detail.getMinimumCalls());
    content.put("failureRateThreshold", detail.getFailureRateThreshold());
    content.put("slowCallRateThreshold", detail.getSlowCallRateThreshold());
    content.put("slowCallDurationMillis", detail.getSlowCallDurationMillis());
    content.put("openStateWaitMillis", detail.getOpenStateWaitMillis());
    content.put("permittedHalfOpenCalls", detail.getPermittedHalfOpenCalls());
    content.put("connectionPoolPolicy", detail.getConnectionPoolPolicy());
    content.put("outlierDetectionPolicy", detail.getOutlierDetectionPolicy());
    content.put("retryBudgetPolicy", detail.getRetryBudgetPolicy());
    content.put("exceptionRecordPolicy", detail.getExceptionRecordPolicy());
    content.put("exceptionIgnorePolicy", detail.getExceptionIgnorePolicy());
    content.put("fallbackPolicy", detail.getFallbackPolicy());
    return content;
  }

  private Map<String, Object> rateLimitContent(GovernanceRuleEntity rule) {
    RateLimitRuleEntity detail =
        rateLimitRuleRepository
            .findById(rule.getId())
            .orElseThrow(() -> new InvalidRuleRequestException("限流规则明细不存在"));
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("limitType", detail.getLimitType());
    content.put("limitAlgorithm", detail.getLimitAlgorithm());
    content.put("enforcementMode", detail.getEnforcementMode());
    content.put("targetSelector", detail.getTargetSelector());
    content.put("dimensions", detail.getDimensions());
    content.put("quotaConfig", detail.getQuotaConfig());
    content.put("windowConfig", detail.getWindowConfig());
    content.put("burstConfig", detail.getBurstConfig());
    content.put("modelLimitConfig", detail.getModelLimitConfig());
    content.put("fallbackPolicy", detail.getFallbackPolicy());
    content.put("responsePolicy", detail.getResponsePolicy());
    return content;
  }

  private Map<String, Object> authContent(GovernanceRuleEntity rule) {
    AuthPolicyRuleEntity detail =
        authPolicyRuleRepository
            .findById(rule.getId())
            .orElseThrow(() -> new InvalidRuleRequestException("鉴权规则明细不存在"));
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("authPolicyType", detail.getAuthPolicyType());
    content.put("authAction", detail.getAuthAction());
    content.put("mtlsMode", detail.getMtlsMode());
    content.put("trustDomain", detail.getTrustDomain());
    content.put("workloadSelector", detail.getWorkloadSelector());
    content.put("peerSources", detail.getPeerSources());
    content.put("requestAuthentications", detail.getRequestAuthentications());
    content.put("authorizationFrom", detail.getAuthorizationFrom());
    content.put("authorizationTo", detail.getAuthorizationTo());
    content.put("authorizationWhen", detail.getAuthorizationWhen());
    content.put("jwtRules", detail.getJwtRules());
    content.put("extAuthzProvider", detail.getExtAuthzProvider());
    content.put("auditPolicy", detail.getAuditPolicy());
    return content;
  }

  private String toStellnulaRuleType(String ruleType) {
    return switch (ruleType) {
      case "ROUTE" -> "ROUTE";
      case "RATE_LIMIT" -> "RATE_LIMIT";
      case "BREAKER" -> "CIRCUIT_BREAKER";
      case "AUTH" -> "AUTH";
      default -> throw new InvalidRuleRequestException("不支持的规则类型: " + ruleType);
    };
  }

  private String targetService(GovernanceRuleEntity rule, ApplicationEntity application) {
    Object targetService = valueFrom(rule.getRuntimeSnapshotJson(), "targetService");
    if (targetService == null) {
      targetService = valueFrom(rule.getRuntimeSnapshotJson(), "service");
    }
    return Objects.toString(targetService, application.getApplicationCode());
  }

  private Object valueFrom(Map<String, Object> source, String key) {
    if (source == null) {
      return null;
    }
    return source.get(key);
  }

  private int defaultPriority(Integer priority) {
    return priority == null ? 1000 : priority;
  }

  private String configId(
      ApplicationEntity application, GovernanceRuleEntity rule, String ruleType) {
    return "stellorbit."
        + normalize(application.getApplicationCode())
        + "."
        + normalize(ruleType)
        + "."
        + normalize(rule.getRuleCode());
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("治理规则内容序列化失败: " + exception.getMessage());
    }
  }

  private String toCanonicalJson(Object value) {
    try {
      return canonicalObjectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("治理规则内容序列化失败: " + exception.getMessage());
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256算法不可用", exception);
    }
  }
}

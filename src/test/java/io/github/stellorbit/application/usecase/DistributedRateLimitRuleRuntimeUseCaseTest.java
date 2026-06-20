package io.github.stellorbit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellflux.caffeine.StellfluxCaffeineCacheFactory;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleConfigResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleDeltaResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleSnapshotResponse;
import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleAggregatePayloadBuilder;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import io.opentelemetry.api.OpenTelemetry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributedRateLimitRuleRuntimeUseCaseTest {

  @Mock private GovernanceRuleRepository governanceRuleRepository;
  @Mock private RateLimitRuleRepository rateLimitRuleRepository;
  @Mock private ApplicationRepository applicationRepository;
  @Mock private GovernanceRuleContentCompiler governanceRuleContentCompiler;

  private ObjectMapper objectMapper;
  private DistributedRateLimitRuleRuntimeUseCase useCase;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    useCase =
        new DistributedRateLimitRuleRuntimeUseCase(
            governanceRuleRepository,
            rateLimitRuleRepository,
            applicationRepository,
            governanceRuleContentCompiler,
            new GovernanceRuleAggregatePayloadBuilder(objectMapper),
            objectMapper,
            new StellfluxCaffeineCacheFactory(
                OpenTelemetry.noop(), StellfluxOpenTelemetryConfig.builder().build()));
  }

  @Test
  void shouldBuildPagedDistributedRateLimitSnapshotWithConfigCenterPayloadFormat()
      throws Exception {
    UUID instanceSpaceId = UUID.randomUUID();
    ApplicationEntity orderApplication =
        application(instanceSpaceId, UUID.randomUUID(), "order-service");
    ApplicationEntity payApplication =
        application(instanceSpaceId, UUID.randomUUID(), "pay-service");
    GovernanceRuleEntity orderRule =
        rateLimitRule(orderApplication, UUID.randomUUID(), "order-global-quota", 10);
    GovernanceRuleEntity payRule =
        rateLimitRule(payApplication, UUID.randomUUID(), "pay-global-quota", 20);

    when(governanceRuleRepository.findEnabledDistributedRateLimitRules())
        .thenReturn(List.of(orderRule, payRule));
    when(rateLimitRuleRepository.findAllById(any()))
        .thenReturn(List.of(rateLimitDetail(orderRule.getId()), rateLimitDetail(payRule.getId())));
    when(applicationRepository.findAllById(any()))
        .thenReturn(List.of(orderApplication, payApplication));
    when(governanceRuleContentCompiler.compile(orderRule, orderApplication))
        .thenReturn(compiledRule(orderRule, orderApplication));
    when(governanceRuleContentCompiler.compile(payRule, payApplication))
        .thenReturn(compiledRule(payRule, payApplication));

    boolean refreshed = useCase.refreshIfChanged();
    DistributedRateLimitRuleSnapshotResponse snapshot = useCase.snapshot(0, 1);

    assertThat(refreshed).isTrue();
    assertThat(snapshot.snapshotVersion()).isEqualTo(1L);
    assertThat(snapshot.totalApplications()).isEqualTo(2);
    assertThat(snapshot.totalRules()).isEqualTo(2);
    assertThat(snapshot.configs().content()).hasSize(1);
    assertThat(snapshot.configs().totalElements()).isEqualTo(2);
    assertThat(snapshot.configs().totalPages()).isEqualTo(2);

    DistributedRateLimitRuleConfigResponse config = snapshot.configs().content().getFirst();
    assertThat(config.ruleType()).isEqualTo("RATE_LIMIT");
    assertThat(config.stellnulaRuleType()).isEqualTo("RATE_LIMIT");
    assertThat(config.publishKind()).isEqualTo("RATE_LIMIT_RULES");
    assertThat(config.ruleCount()).isEqualTo(1);

    JsonNode content = objectMapper.readTree(config.content());
    assertThat(content.path("ruleType").asText()).isEqualTo("RATE_LIMIT");
    assertThat(content.path("sourceRuleType").asText()).isEqualTo("RATE_LIMIT");
    assertThat(content.path("rules")).hasSize(1);
    assertThat(content.path("limit")).hasSize(1);
    assertThat(
            content.path("rules").get(0).path("content").path("limit").path("limitMode").asText())
        .isEqualTo("QPS");
    assertThat(
            content
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("trafficProtocol")
                .asText())
        .isEqualTo("HTTP");
    assertThat(
            content
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("coordinationMode")
                .asText())
        .isEqualTo("GLOBAL_QUOTA");
    assertThat(
            content
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("executionLocation")
                .asText())
        .isEqualTo("APPLICATION");
    assertThat(
            content
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("enforcementMode")
                .asText())
        .isEqualTo("GLOBAL_QUOTA");
    assertThat(
            content
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("keyExtractor")
                .path("keys"))
        .hasSize(1);

    assertThat(useCase.refreshIfChanged()).isFalse();
    assertThat(useCase.snapshot(0, 10).snapshotVersion()).isEqualTo(1L);
    assertThat(useCase.cacheTelemetry())
        .containsEntry("cacheName", "stellorbit-distributed-rate-limit-snapshot")
        .containsEntry("estimatedSize", 1L)
        .containsEntry("snapshotVersion", 1L)
        .containsEntry("totalApplications", 2)
        .containsEntry("totalRules", 2);
  }

  @Test
  void shouldReturnOnlyChangedConfigInDeltaAfterInitialFullSnapshot() throws Exception {
    UUID instanceSpaceId = UUID.randomUUID();
    ApplicationEntity orderApplication =
        application(instanceSpaceId, UUID.randomUUID(), "order-service");
    ApplicationEntity payApplication =
        application(instanceSpaceId, UUID.randomUUID(), "pay-service");
    GovernanceRuleEntity orderRule =
        rateLimitRule(orderApplication, UUID.randomUUID(), "order-global-quota", 10);
    GovernanceRuleEntity payRule =
        rateLimitRule(payApplication, UUID.randomUUID(), "pay-global-quota", 20);

    when(governanceRuleRepository.findEnabledDistributedRateLimitRules())
        .thenReturn(List.of(orderRule, payRule), List.of(orderRule, payRule));
    when(rateLimitRuleRepository.findAllById(any()))
        .thenReturn(
            List.of(
                rateLimitDetail(orderRule.getId(), 1000), rateLimitDetail(payRule.getId(), 1000)),
            List.of(
                rateLimitDetail(orderRule.getId(), 2000), rateLimitDetail(payRule.getId(), 1000)));
    when(applicationRepository.findAllById(any()))
        .thenReturn(List.of(orderApplication, payApplication));
    when(governanceRuleContentCompiler.compile(orderRule, orderApplication))
        .thenReturn(
            compiledRule(orderRule, orderApplication, 1000),
            compiledRule(orderRule, orderApplication, 2000));
    when(governanceRuleContentCompiler.compile(payRule, payApplication))
        .thenReturn(compiledRule(payRule, payApplication, 1000));

    assertThat(useCase.refreshIfChanged()).isTrue();
    DistributedRateLimitRuleSnapshotResponse baseline = useCase.snapshot(0, 10);

    assertThat(useCase.refreshIfChanged()).isTrue();
    DistributedRateLimitRuleDeltaResponse delta =
        useCase.deltaSince(baseline.snapshotVersion(), baseline.checksum());

    assertThat(delta.fromSnapshotVersion()).isEqualTo(1L);
    assertThat(delta.toSnapshotVersion()).isEqualTo(2L);
    assertThat(delta.changeCount()).isEqualTo(1);
    assertThat(delta.changes()).hasSize(1);
    assertThat(delta.changes().getFirst().operation()).isEqualTo("UPSERT_CONFIG");
    assertThat(delta.changes().getFirst().applicationCode()).isEqualTo("order-service");
    assertThat(delta.changes().getFirst().config()).isNotNull();
    assertThat(delta.changes().getFirst().previousChecksum()).isNotBlank();
    assertThat(delta.changes().getFirst().currentChecksum())
        .isEqualTo(delta.changes().getFirst().config().checksum());

    JsonNode updatedContent = objectMapper.readTree(delta.changes().getFirst().config().content());
    assertThat(
            updatedContent
                .path("rules")
                .get(0)
                .path("content")
                .path("limit")
                .path("quotaConfig")
                .path("limit")
                .asInt())
        .isEqualTo(2000);
    assertThat(useCase.requiresFullSync(baseline.snapshotVersion(), baseline.checksum())).isFalse();
    assertThat(useCase.requiresFullSync(null, null)).isTrue();
    assertThat(useCase.deltaSince(2L, useCase.latestChecksum()).changeCount()).isZero();
  }

  private ApplicationEntity application(UUID instanceSpaceId, UUID applicationId, String code) {
    ApplicationEntity application = new ApplicationEntity();
    application.setId(applicationId);
    application.setInstanceSpaceId(instanceSpaceId);
    application.setApplicationCode(code);
    application.setApplicationName(code);
    application.setCreatedBy("test");
    application.setUpdatedBy("test");
    return application;
  }

  private GovernanceRuleEntity rateLimitRule(
      ApplicationEntity application, UUID ruleId, String ruleCode, int priority) {
    GovernanceRuleEntity rule = new GovernanceRuleEntity();
    rule.setId(ruleId);
    rule.setInstanceSpaceId(application.getInstanceSpaceId());
    rule.setApplicationId(application.getId());
    rule.setRuleCode(ruleCode);
    rule.setRuleName("Rule " + ruleCode);
    rule.setRuleType("RATE_LIMIT");
    rule.setSourceFormat("CUE");
    rule.setRuntimeFormat("JSON");
    rule.setCueSource("{}");
    rule.setPriority(priority);
    rule.setEnabled(true);
    rule.setStatus("DRAFT");
    rule.setDraftVersion(1L);
    rule.setCreatedBy("test");
    rule.setUpdatedBy("test");
    rule.setRowVersion(1L);
    return rule;
  }

  private RateLimitRuleEntity rateLimitDetail(UUID ruleId) {
    return rateLimitDetail(ruleId, 1000);
  }

  private RateLimitRuleEntity rateLimitDetail(UUID ruleId, int quotaLimit) {
    RateLimitRuleEntity detail = new RateLimitRuleEntity();
    detail.setId(ruleId);
    detail.setLimitMode("QPS");
    detail.setLimitType("REQUEST");
    detail.setLimitAlgorithm("TOKEN_BUCKET");
    detail.setTrafficProtocol("HTTP");
    detail.setExecutionLocation("APPLICATION");
    detail.setCoordinationMode("GLOBAL_QUOTA");
    detail.setEnforcementMode("GLOBAL_QUOTA");
    detail.setTargetSelector(Map.of("path", "/api/orders"));
    detail.setRequestMatcher(Map.of("http", Map.of("method", "GET", "path", "/api/orders")));
    detail.setKeyExtractor(
        Map.of(
            "strategy",
            "COMPOSITE",
            "keys",
            List.of(Map.of("name", "tenant", "source", "HEADER", "key", "x-tenant-id"))));
    detail.setDimensions(
        List.of(Map.of("name", "tenant", "source", "HEADER", "key", "x-tenant-id")));
    detail.setQuotaConfig(Map.of("limit", quotaLimit, "unit", "REQUEST", "period", "SECOND"));
    detail.setWindowConfig(Map.of("windowType", "SLIDING", "durationMillis", 60000));
    detail.setBurstConfig(new LinkedHashMap<>());
    detail.setConcurrencyConfig(Map.of("maxConcurrent", 100));
    detail.setHotspotConfig(Map.of("enabled", true, "topN", 100, "threshold", 1000));
    detail.setCustomPolicy(Map.of("policyType", "EXPRESSION", "expression", "request.tenant"));
    detail.setModelLimitConfig(new LinkedHashMap<>());
    detail.setFallbackPolicy(Map.of("mode", "FAIL_OPEN"));
    detail.setResponsePolicy(Map.of("status", 429));
    detail.setObservabilityConfig(Map.of("metricLabels", List.of("tenant")));
    detail.setShadowConfig(Map.of("enabled", false));
    return detail;
  }

  private CompiledGovernanceRule compiledRule(
      GovernanceRuleEntity rule, ApplicationEntity application) {
    return compiledRule(rule, application, 1000);
  }

  private CompiledGovernanceRule compiledRule(
      GovernanceRuleEntity rule, ApplicationEntity application, int quotaLimit) {
    Map<String, Object> limit = new LinkedHashMap<>();
    limit.put("limitMode", "QPS");
    limit.put("limitType", "REQUEST");
    limit.put("limitAlgorithm", "TOKEN_BUCKET");
    limit.put("trafficProtocol", "HTTP");
    limit.put("executionLocation", "APPLICATION");
    limit.put("coordinationMode", "GLOBAL_QUOTA");
    limit.put("enforcementMode", "GLOBAL_QUOTA");
    limit.put("distributedCoordination", true);
    limit.put("targetSelector", Map.of("path", "/api/orders"));
    limit.put("requestMatcher", Map.of("http", Map.of("method", "GET", "path", "/api/orders")));
    limit.put(
        "keyExtractor",
        Map.of(
            "strategy",
            "COMPOSITE",
            "keys",
            List.of(Map.of("name", "tenant", "source", "HEADER", "key", "x-tenant-id"))));
    limit.put(
        "dimensions", List.of(Map.of("name", "tenant", "source", "HEADER", "key", "x-tenant-id")));
    limit.put("quotaConfig", Map.of("limit", quotaLimit, "unit", "REQUEST", "period", "SECOND"));
    limit.put("windowConfig", Map.of("windowType", "SLIDING", "durationMillis", 60000));
    limit.put("concurrencyConfig", Map.of("maxConcurrent", 100));
    limit.put("hotspotConfig", Map.of("enabled", true, "topN", 100, "threshold", 1000));
    limit.put("customPolicy", Map.of("policyType", "EXPRESSION", "expression", "request.tenant"));
    limit.put("fallbackPolicy", Map.of("mode", "FAIL_OPEN"));
    limit.put("responsePolicy", Map.of("status", 429));
    limit.put("observabilityConfig", Map.of("metricLabels", List.of("tenant")));
    limit.put("shadowConfig", Map.of("enabled", false));

    Map<String, Object> content = new LinkedHashMap<>();
    content.put("ruleType", "RATE_LIMIT");
    content.put("targetService", application.getApplicationCode());
    content.put("status", "ACTIVE");
    content.put("priority", rule.getPriority());
    content.put("schemaVersion", "stellorbit.governance.v1");
    content.put("ruleCode", rule.getRuleCode());
    content.put("ruleName", rule.getRuleName());
    content.put("draftVersion", rule.getDraftVersion());
    content.put("sourceFormat", rule.getSourceFormat());
    content.put("runtimeFormat", rule.getRuntimeFormat());
    content.put("enabled", rule.getEnabled());
    content.put("limit", limit);

    return new CompiledGovernanceRule(
        rule,
        "stellorbit." + application.getApplicationCode() + ".rate_limit",
        "RATE_LIMIT",
        application.getApplicationCode(),
        "ACTIVE",
        rule.getPriority(),
        "stellorbit.governance.v1",
        "{}",
        "rule-checksum-" + rule.getRuleCode(),
        content,
        List.of(),
        List.of());
  }
}

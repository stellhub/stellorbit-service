package io.github.stellorbit.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GovernanceRuleAggregatePayloadBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GovernanceRuleAggregatePayloadBuilder builder =
      new GovernanceRuleAggregatePayloadBuilder(objectMapper);

  @Test
  void shouldBuildFixedTypeConfigsWithAggregatedRules() throws Exception {
    ApplicationEntity application = new ApplicationEntity();
    application.setApplicationCode("Demo_App");
    OffsetDateTime generatedAt = OffsetDateTime.parse("2026-06-17T10:15:30+08:00");

    List<AggregatedGovernanceRuleConfig> configs =
        builder.build(
            application, 7L, "release-7", "JSON", generatedAt, List.of(routeRule("route-a")));

    assertThat(configs)
        .extracting(AggregatedGovernanceRuleConfig::configId)
        .containsExactly(
            "stellorbit.demo_app.route",
            "stellorbit.demo_app.circuit_breaker",
            "stellorbit.demo_app.rate_limit",
            "stellorbit.demo_app.auth");

    AggregatedGovernanceRuleConfig routeConfig = configs.getFirst();
    JsonNode routePayload = objectMapper.readTree(routeConfig.content());
    assertThat(routePayload.path("releaseVersion").asLong()).isEqualTo(7L);
    assertThat(routePayload.path("generatedAt").asText()).isEqualTo(generatedAt.toString());
    assertThat(routePayload.path("checksum").asText()).isEqualTo(routeConfig.aggregateChecksum());
    assertThat(routePayload.path("rules")).hasSize(1);
    assertThat(routePayload.path("rules").get(0).path("ruleCode").asText()).isEqualTo("route-a");
    assertThat(routePayload.path("routes")).hasSize(1);

    JsonNode breakerPayload = objectMapper.readTree(configs.get(1).content());
    assertThat(breakerPayload.path("ruleCount").asInt()).isZero();
    assertThat(breakerPayload.path("rules").size()).isZero();
    assertThat(breakerPayload.path("status").asText()).isEqualTo("DISABLED");
  }

  private CompiledGovernanceRule routeRule(String ruleCode) {
    GovernanceRuleEntity rule = new GovernanceRuleEntity();
    rule.setId(UUID.randomUUID());
    rule.setRuleType("ROUTE");
    rule.setRuleCode(ruleCode);
    rule.setRuleName("Route " + ruleCode);
    rule.setDraftVersion(3L);
    rule.setPriority(10);
    Map<String, Object> content =
        Map.of(
            "ruleType", "ROUTE",
            "targetService", "order-service",
            "status", "ACTIVE",
            "priority", 10,
            "routes", List.of(Map.of("weight", 100)));
    return new CompiledGovernanceRule(
        rule,
        "stellorbit.demo_app.route",
        "ROUTE",
        "order-service",
        "ACTIVE",
        10,
        "stellorbit.governance.v1",
        "{}",
        "rule-checksum",
        content,
        List.of(),
        List.of());
  }
}

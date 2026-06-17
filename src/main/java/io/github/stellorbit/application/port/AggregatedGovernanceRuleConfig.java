package io.github.stellorbit.application.port;

import java.util.List;
import java.util.Map;

public record AggregatedGovernanceRuleConfig(
    String configId,
    String ruleName,
    String ruleType,
    String stellnulaRuleType,
    String publishKind,
    String content,
    String checksum,
    String aggregateChecksum,
    Map<String, Object> contentModel,
    List<CompiledGovernanceRule> rules) {}

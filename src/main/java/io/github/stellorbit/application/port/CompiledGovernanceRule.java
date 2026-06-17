package io.github.stellorbit.application.port;

import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import java.util.List;
import java.util.Map;

public record CompiledGovernanceRule(
    GovernanceRuleEntity rule,
    String configId,
    String stellnulaRuleType,
    String targetService,
    String status,
    Integer priority,
    String schemaVersion,
    String content,
    String checksum,
    Map<String, Object> contentModel,
    List<String> warnings,
    List<String> explain) {}

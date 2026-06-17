package io.github.stellorbit.api.dto;

import java.util.Map;
import java.util.UUID;

public record RuntimeSnapshotRuleResponse(
    UUID ruleId,
    String ruleType,
    String ruleCode,
    String ruleName,
    Long draftVersion,
    Integer priority,
    String checksum,
    Map<String, Object> snapshotJson) {}

package io.github.stellorbit.interfaces.http.dto;

import java.util.Map;
import java.util.UUID;

public record RuleReleaseItemDiffResponse(
    UUID ruleId,
    String ruleCode,
    String ruleName,
    String ruleType,
    String changeType,
    String baseChecksum,
    String targetChecksum,
    Map<String, Object> baseSnapshot,
    Map<String, Object> targetSnapshot) {}

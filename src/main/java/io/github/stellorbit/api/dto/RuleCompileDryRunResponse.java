package io.github.stellorbit.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleCompileDryRunResponse(
    UUID ruleId,
    String ruleType,
    String ruleCode,
    String ruleName,
    String schemaVersion,
    String configId,
    String targetService,
    String runtimeFormat,
    String checksum,
    Map<String, Object> normalizedSnapshotJson,
    String jsonContent,
    String protobufContentBase64,
    List<String> errors,
    List<String> warnings,
    List<String> explain) {}

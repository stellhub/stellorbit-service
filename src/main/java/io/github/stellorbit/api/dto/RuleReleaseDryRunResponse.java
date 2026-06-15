package io.github.stellorbit.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleReleaseDryRunResponse(
    UUID instanceSpaceId,
    UUID applicationId,
    Long releaseVersion,
    String runtimeFormat,
    Map<String, Object> releaseSnapshotJson,
    List<RuleCompileDryRunResponse> rules,
    List<String> errors,
    List<String> warnings,
    List<String> explain) {}

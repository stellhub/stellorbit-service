package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DistributedRateLimitRuleDeltaResponse(
    Long fromSnapshotVersion,
    Long toSnapshotVersion,
    String fromChecksum,
    String toChecksum,
    OffsetDateTime generatedAt,
    Integer changeCount,
    List<DistributedRateLimitRuleConfigChangeResponse> changes) {}

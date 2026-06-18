package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;

public record DistributedRateLimitRuleSnapshotResponse(
    Long snapshotVersion,
    String checksum,
    OffsetDateTime generatedAt,
    Integer totalApplications,
    Integer totalRules,
    PageResponse<DistributedRateLimitRuleConfigResponse> configs) {}

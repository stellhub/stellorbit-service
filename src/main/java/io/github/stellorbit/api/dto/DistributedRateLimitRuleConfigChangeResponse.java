package io.github.stellorbit.api.dto;

import java.util.UUID;

public record DistributedRateLimitRuleConfigChangeResponse(
    String operation,
    Long fromSnapshotVersion,
    Long toSnapshotVersion,
    UUID instanceSpaceId,
    UUID applicationId,
    String applicationCode,
    String configId,
    String previousChecksum,
    String currentChecksum,
    DistributedRateLimitRuleConfigResponse config) {}

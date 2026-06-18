package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;

public record DistributedRateLimitRuleWatchEventResponse(
    String eventId,
    String eventType,
    Long currentSnapshotVersion,
    Long latestSnapshotVersion,
    String latestChecksum,
    OffsetDateTime generatedAt,
    DistributedRateLimitRuleSnapshotResponse snapshot) {}

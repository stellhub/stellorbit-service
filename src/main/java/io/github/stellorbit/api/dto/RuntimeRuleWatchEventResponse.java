package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;

public record RuntimeRuleWatchEventResponse(
    String eventId,
    String eventType,
    Long currentReleaseVersion,
    Long latestReleaseVersion,
    OffsetDateTime generatedAt,
    RuntimeRuleSnapshotResponse snapshot) {}

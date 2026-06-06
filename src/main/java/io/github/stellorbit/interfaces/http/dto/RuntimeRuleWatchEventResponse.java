package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;

public record RuntimeRuleWatchEventResponse(
    String eventId,
    String eventType,
    Long currentReleaseVersion,
    Long latestReleaseVersion,
    OffsetDateTime generatedAt,
    RuntimeRuleSnapshotResponse snapshot) {}

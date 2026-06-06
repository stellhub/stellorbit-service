package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientRuleStatusReportResponse(
    UUID id, Boolean accepted, Long latestReleaseVersion, OffsetDateTime recordedAt) {}

package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientRuleAckResponse(
    UUID id, Boolean accepted, Long latestReleaseVersion, OffsetDateTime recordedAt) {}

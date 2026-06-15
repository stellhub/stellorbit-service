package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClientInstanceViewResponse(
    UUID instanceSpaceId,
    UUID applicationId,
    OffsetDateTime generatedAt,
    RuntimeNodeDirectoryResponse rateLimitNodeDirectory,
    List<ClientRuntimeInstanceResponse> instances) {}

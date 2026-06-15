package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RuntimeNodeDirectoryResponse(
    String hashStrategy,
    String hashKeyHint,
    String consistencyMode,
    String ringVersion,
    String currentNodeId,
    OffsetDateTime generatedAt,
    List<RuntimeNodeResponse> nodes) {}

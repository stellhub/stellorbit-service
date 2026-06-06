package io.github.stellorbit.infrastructure.cue;

import java.util.List;
import java.util.Map;

public record CueCompilationResult(
    String schemaVersion,
    Map<String, Object> normalizedModel,
    List<String> warnings,
    List<String> explain) {}

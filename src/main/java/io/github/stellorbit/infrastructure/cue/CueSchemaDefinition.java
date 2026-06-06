package io.github.stellorbit.infrastructure.cue;

public record CueSchemaDefinition(
    String ruleType, String schemaVersion, String schemaName, String cueSchema) {}

package io.github.stellorbit.interfaces.http.dto;

import java.util.List;
import java.util.UUID;

public record BatchRuleEnabledResponse(
    Boolean enabled, Integer updatedCount, List<UUID> updatedRuleIds) {}

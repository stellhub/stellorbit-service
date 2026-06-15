package io.github.stellorbit.api.dto;

import java.util.List;
import java.util.UUID;

public record RuleReleaseDiffResponse(
    UUID baseReleaseId,
    UUID targetReleaseId,
    List<RuleReleaseItemDiffResponse> ruleDiffs,
    List<StellnulaConfigDiffResponse> configDiffs) {}

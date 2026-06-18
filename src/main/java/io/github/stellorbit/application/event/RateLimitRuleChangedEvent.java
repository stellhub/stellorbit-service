package io.github.stellorbit.application.event;

import java.util.UUID;

public record RateLimitRuleChangedEvent(UUID ruleId, String action) {}

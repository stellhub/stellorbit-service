package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchRuleEnabledRequest(
    @NotEmpty(message = "规则ID不能为空") List<UUID> ruleIds,
    @NotNull(message = "启停状态不能为空") Boolean enabled,
    String operator,
    String reason) {}

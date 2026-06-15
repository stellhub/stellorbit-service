package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record PublishGovernanceRulesRequest(
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    @NotNull(message = "发布版本不能为空") @Positive(message = "发布版本必须大于0") Long releaseVersion,
    @NotBlank(message = "发布名称不能为空") String releaseName,
    String runtimeFormat,
    String idempotencyKey,
    Integer maxRetryCount,
    List<UUID> ruleIds,
    String releaseNote,
    String env,
    String region,
    String zone,
    String cluster,
    String scopeMode,
    String configGroup,
    String publishedBy) {}

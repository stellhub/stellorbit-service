package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleMutationRequest(
    UUID id,
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    @NotBlank(message = "规则编码不能为空") String ruleCode,
    @NotBlank(message = "规则名称不能为空") String ruleName,
    String sourceFormat,
    String runtimeFormat,
    @NotBlank(message = "CUE规则原文不能为空") String cueSource,
    Map<String, Object> runtimeSnapshotJson,
    String checksum,
    Integer priority,
    Boolean enabled,
    String status,
    Long draftVersion,
    String description,
    List<Object> tags,
    @NotBlank(message = "创建人不能为空") String createdBy,
    @NotBlank(message = "更新人不能为空") String updatedBy) {}

package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RateLimitUsageReportRequest(
    UUID assignmentId,
    @NotNull(message = "限流规则ID不能为空") UUID rateLimitRuleId,
    @NotNull(message = "发布ID不能为空") UUID releaseId,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "限流Key不能为空") String limitKey,
    @PositiveOrZero(message = "已使用配额不能小于0") Long reportedUsed,
    @PositiveOrZero(message = "放行数不能小于0") Long reportedAllowed,
    @PositiveOrZero(message = "拒绝数不能小于0") Long reportedRejected,
    Map<String, Object> modelUsage,
    @NotNull(message = "上报窗口开始时间不能为空") OffsetDateTime reportWindowStart,
    @NotNull(message = "上报窗口结束时间不能为空") OffsetDateTime reportWindowEnd) {}

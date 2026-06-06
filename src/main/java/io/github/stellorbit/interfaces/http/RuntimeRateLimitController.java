package io.github.stellorbit.interfaces.http;

import io.github.stellorbit.application.runtime.LocalRateLimitCounterStore;
import io.github.stellorbit.application.runtime.RateLimitDecisionBuffer;
import io.github.stellorbit.application.runtime.RateLimitRuntimeMetrics;
import io.github.stellorbit.application.runtime.RuleRuntimeCache;
import io.github.stellorbit.application.runtime.RuntimeNodeDirectory;
import io.github.stellorbit.application.usecase.EvaluateRateLimitUseCase;
import io.github.stellorbit.application.usecase.RateLimitQuotaLeaseUseCase;
import io.github.stellorbit.application.usecase.RateLimitUsageReportUseCase;
import io.github.stellorbit.interfaces.http.dto.EvaluateRateLimitRequest;
import io.github.stellorbit.interfaces.http.dto.EvaluateRateLimitResponse;
import io.github.stellorbit.interfaces.http.dto.RateLimitQuotaLeaseRequest;
import io.github.stellorbit.interfaces.http.dto.RateLimitQuotaLeaseResponse;
import io.github.stellorbit.interfaces.http.dto.RateLimitUsageReportRequest;
import io.github.stellorbit.interfaces.http.dto.RateLimitUsageReportResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeNodeDirectoryResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/runtime/rate-limit")
public class RuntimeRateLimitController {

  private final EvaluateRateLimitUseCase evaluateRateLimitUseCase;
  private final RateLimitUsageReportUseCase rateLimitUsageReportUseCase;
  private final RateLimitQuotaLeaseUseCase rateLimitQuotaLeaseUseCase;
  private final RuntimeNodeDirectory runtimeNodeDirectory;
  private final RuleRuntimeCache ruleRuntimeCache;
  private final LocalRateLimitCounterStore localRateLimitCounterStore;
  private final RateLimitRuntimeMetrics metrics;
  private final RateLimitDecisionBuffer decisionBuffer;

  public RuntimeRateLimitController(
      EvaluateRateLimitUseCase evaluateRateLimitUseCase,
      RateLimitUsageReportUseCase rateLimitUsageReportUseCase,
      RateLimitQuotaLeaseUseCase rateLimitQuotaLeaseUseCase,
      RuntimeNodeDirectory runtimeNodeDirectory,
      RuleRuntimeCache ruleRuntimeCache,
      LocalRateLimitCounterStore localRateLimitCounterStore,
      RateLimitRuntimeMetrics metrics,
      RateLimitDecisionBuffer decisionBuffer) {
    this.evaluateRateLimitUseCase = evaluateRateLimitUseCase;
    this.rateLimitUsageReportUseCase = rateLimitUsageReportUseCase;
    this.rateLimitQuotaLeaseUseCase = rateLimitQuotaLeaseUseCase;
    this.runtimeNodeDirectory = runtimeNodeDirectory;
    this.ruleRuntimeCache = ruleRuntimeCache;
    this.localRateLimitCounterStore = localRateLimitCounterStore;
    this.metrics = metrics;
    this.decisionBuffer = decisionBuffer;
  }

  /** 查询运行时限流节点目录，供客户端固定hash直连。 */
  @GetMapping("/nodes")
  public RuntimeNodeDirectoryResponse nodes() {
    return runtimeNodeDirectory.currentDirectory();
  }

  /** 查询本地运行时限流缓存状态。 */
  @GetMapping("/local-state")
  public Map<String, Object> localState() {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("rateLimitRuleCount", ruleRuntimeCache.rateLimitRuleCount());
    state.put("localBucketCount", localRateLimitCounterStore.bucketCount());
    state.put("ruleLastLoadedAt", ruleRuntimeCache.getLastLoadedAt());
    state.put("ruleWatermark", ruleRuntimeCache.getWatermark());
    state.put("metrics", metrics.snapshot());
    state.put("queuedDecisionCount", decisionBuffer.queuedDecisionCount());
    state.put("reportedAt", OffsetDateTime.now());
    return state;
  }

  /** 执行运行时限流判定。 */
  @PostMapping("/evaluate")
  public EvaluateRateLimitResponse evaluate(@Valid @RequestBody EvaluateRateLimitRequest request) {
    return evaluateRateLimitUseCase.evaluate(request);
  }

  /** 客户端周期上报限流用量。 */
  @PostMapping("/usage-reports")
  public RateLimitUsageReportResponse reportUsage(
      @Valid @RequestBody RateLimitUsageReportRequest request) {
    return rateLimitUsageReportUseCase.report(request);
  }

  /** 为客户端分配或续租配额租约。 */
  @PostMapping("/assignments/lease")
  public RateLimitQuotaLeaseResponse leaseQuota(
      @Valid @RequestBody RateLimitQuotaLeaseRequest request) {
    return rateLimitQuotaLeaseUseCase.lease(request);
  }
}

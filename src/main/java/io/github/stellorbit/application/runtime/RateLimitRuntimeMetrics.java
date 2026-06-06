package io.github.stellorbit.application.runtime;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class RateLimitRuntimeMetrics {

  private final LongAdder totalRequests = new LongAdder();
  private final LongAdder allowedRequests = new LongAdder();
  private final LongAdder rejectedRequests = new LongAdder();
  private final LongAdder fallbackAllowedRequests = new LongAdder();
  private final LongAdder sampledDecisionDrops = new LongAdder();

  public void recordDecision(LocalRateLimitDecision decision) {
    totalRequests.increment();
    if (decision.allowed()) {
      allowedRequests.increment();
      if (decision.fallbackUsed()) {
        fallbackAllowedRequests.increment();
      }
      return;
    }
    rejectedRequests.increment();
  }

  public void recordDecisionSampleDrop() {
    sampledDecisionDrops.increment();
  }

  public Map<String, Long> snapshot() {
    return Map.of(
        "totalRequests", totalRequests.sum(),
        "allowedRequests", allowedRequests.sum(),
        "rejectedRequests", rejectedRequests.sum(),
        "fallbackAllowedRequests", fallbackAllowedRequests.sum(),
        "sampledDecisionDrops", sampledDecisionDrops.sum());
  }
}

package io.github.stellorbit.application.runtime;

import io.github.stellorbit.infrastructure.persistence.entity.RateLimitDecisionEntity;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitDecisionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimitDecisionBuffer {

  private final RuntimeRateLimitProperties properties;
  private final RateLimitDecisionRepository rateLimitDecisionRepository;
  private final RateLimitRuntimeMetrics metrics;
  private final Queue<SampledDecision> queue = new ConcurrentLinkedQueue<>();

  public RateLimitDecisionBuffer(
      RuntimeRateLimitProperties properties,
      RateLimitDecisionRepository rateLimitDecisionRepository,
      RateLimitRuntimeMetrics metrics) {
    this.properties = properties;
    this.rateLimitDecisionRepository = rateLimitDecisionRepository;
    this.metrics = metrics;
  }

  /** 按采样率把判定结果放入内存队列，避免热路径写DB。 */
  public void sample(
      RateLimitRuntimeRule rule,
      LocalRateLimitDecision decision,
      String requestId,
      String clientId,
      Map<String, Object> modelRequestUnits) {
    if (properties.getDecisionSampleRate() <= 0) {
      return;
    }
    if (ThreadLocalRandom.current().nextDouble() > properties.getDecisionSampleRate()) {
      return;
    }
    if (queue.size() >= properties.getDecisionBufferCapacity()) {
      metrics.recordDecisionSampleDrop();
      return;
    }
    queue.offer(
        new SampledDecision(
            rule,
            decision,
            requestId,
            clientId,
            modelRequestUnits == null ? Map.of() : new LinkedHashMap<>(modelRequestUnits)));
  }

  /** 批量落库采样判定结果。 */
  @Scheduled(fixedDelayString = "${stellorbit.runtime.decision-flush-fixed-delay-millis:1000}")
  public void flush() {
    if (queue.isEmpty()) {
      return;
    }
    ArrayList<RateLimitDecisionEntity> entities = new ArrayList<>();
    for (int i = 0; i < properties.getDecisionFlushBatchSize(); i++) {
      SampledDecision sampled = queue.poll();
      if (sampled == null) {
        break;
      }
      entities.add(toEntity(sampled));
    }
    if (!entities.isEmpty()) {
      rateLimitDecisionRepository.saveAll(entities);
    }
  }

  public int queuedDecisionCount() {
    return queue.size();
  }

  private RateLimitDecisionEntity toEntity(SampledDecision sampled) {
    RateLimitRuntimeRule rule = sampled.rule();
    LocalRateLimitDecision decision = sampled.decision();
    RateLimitDecisionEntity entity = new RateLimitDecisionEntity();
    entity.setBucketId(decision.bucketId());
    entity.setInstanceSpaceId(rule.instanceSpaceId());
    entity.setApplicationId(rule.applicationId());
    entity.setRateLimitRuleId(rule.rateLimitRuleId());
    entity.setReleaseId(rule.releaseId());
    entity.setRequestId(sampled.requestId());
    entity.setClientId(sampled.clientId());
    entity.setLimitKeyHash(decision.limitKeyHash());
    entity.setRequestedPermits(decision.requestedPermits());
    entity.setModelRequestUnits(new LinkedHashMap<>(sampled.modelRequestUnits()));
    entity.setAllowed(decision.allowed());
    entity.setRemainingPermits(decision.remainingPermits());
    entity.setRetryAfterMillis(decision.retryAfterMillis());
    entity.setFallbackUsed(decision.fallbackUsed());
    entity.setDecisionReason(decision.decisionReason());
    return entity;
  }

  private record SampledDecision(
      RateLimitRuntimeRule rule,
      LocalRateLimitDecision decision,
      String requestId,
      String clientId,
      Map<String, Object> modelRequestUnits,
      OffsetDateTime sampledAt) {
    private SampledDecision(
        RateLimitRuntimeRule rule,
        LocalRateLimitDecision decision,
        String requestId,
        String clientId,
        Map<String, Object> modelRequestUnits) {
      this(rule, decision, requestId, clientId, modelRequestUnits, OffsetDateTime.now());
    }
  }
}

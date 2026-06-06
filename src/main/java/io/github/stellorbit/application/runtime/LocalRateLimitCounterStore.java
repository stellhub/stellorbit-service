package io.github.stellorbit.application.runtime;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LocalRateLimitCounterStore {

  private final RuntimeRateLimitProperties properties;
  private final ConcurrentHashMap<String, LocalBucket> buckets = new ConcurrentHashMap<>();

  public LocalRateLimitCounterStore(RuntimeRateLimitProperties properties) {
    this.properties = properties;
  }

  /** 使用本地内存窗口计数执行限流判定。 */
  public LocalRateLimitDecision evaluate(
      RateLimitRuntimeRule rule,
      String limitKeyHash,
      String shardingSeed,
      long requestedPermits,
      OffsetDateTime now) {
    int shardCount = Math.max(rule.hotspotShardCount(), 1);
    int shard = resolveShard(limitKeyHash, shardingSeed, shardCount);
    String effectiveLimitKeyHash =
        shardCount <= 1 ? limitKeyHash : limitKeyHash + "#shard-" + shard;
    WindowRange window = resolveWindow(rule.windowMillis(), now);
    String bucketKey = bucketKey(rule, effectiveLimitKeyHash, window.startAt());
    AtomicReference<LocalRateLimitDecision> decisionRef = new AtomicReference<>();
    buckets.compute(
        bucketKey,
        (key, current) -> {
          LocalBucket bucket =
              current == null ? newBucket(rule, effectiveLimitKeyHash, window) : current;
          boolean allowed = bucket.remainingPermits >= requestedPermits;
          if (allowed) {
            bucket.usedPermits += requestedPermits;
            bucket.remainingPermits -= requestedPermits;
          }
          decisionRef.set(
              toDecision(
                  rule,
                  effectiveLimitKeyHash,
                  limitKeyHash,
                  shardCount <= 1 ? null : shard,
                  requestedPermits,
                  bucket,
                  allowed,
                  window));
          return bucket;
        });
    return decisionRef.get();
  }

  /** 定时清理本地过期限流窗口。 */
  @Scheduled(fixedDelayString = "${stellorbit.runtime.counter-cleanup-fixed-delay-millis:1000}")
  public void cleanupExpiredBuckets() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Iterator<Map.Entry<String, LocalBucket>> iterator = buckets.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, LocalBucket> entry = iterator.next();
      if (!entry.getValue().expiresAt.isAfter(now)) {
        iterator.remove();
      }
    }
  }

  /** 返回当前本地窗口数量。 */
  public int bucketCount() {
    return buckets.size();
  }

  private LocalBucket newBucket(
      RateLimitRuntimeRule rule, String limitKeyHash, WindowRange window) {
    return new LocalBucket(
        bucketId(rule, limitKeyHash, window.startAt()),
        window.startAt(),
        window.endAt(),
        window.endAt(),
        window.endAt().plus(Duration.ofMillis(properties.getCounterExpireAfterWindowMillis())),
        quotaPerShard(rule),
        0L,
        quotaPerShard(rule));
  }

  private LocalRateLimitDecision toDecision(
      RateLimitRuntimeRule rule,
      String limitKeyHash,
      String originalLimitKeyHash,
      Integer hotspotShard,
      long requestedPermits,
      LocalBucket bucket,
      boolean allowed,
      WindowRange window) {
    return new LocalRateLimitDecision(
        UUID.randomUUID(),
        bucket.id,
        limitKeyHash,
        originalLimitKeyHash,
        hotspotShard,
        rule.limitAlgorithm(),
        bucket.windowStartAt,
        bucket.windowEndAt,
        bucket.quota,
        requestedPermits,
        bucket.usedPermits,
        bucket.remainingPermits,
        allowed,
        allowed ? null : retryAfterMillis(window.endAt()),
        allowed ? "LOCAL_MEMORY_PERMIT_AVAILABLE" : "LOCAL_MEMORY_QUOTA_EXHAUSTED",
        false,
        bucket.resetAt);
  }

  private long quotaPerShard(RateLimitRuntimeRule rule) {
    int shardCount = Math.max(rule.hotspotShardCount(), 1);
    return Math.max(Math.ceilDiv(rule.quota(), shardCount), 1L);
  }

  private int resolveShard(String limitKeyHash, String shardingSeed, int shardCount) {
    if (shardCount <= 1) {
      return 0;
    }
    String seed = shardingSeed == null || shardingSeed.isBlank() ? limitKeyHash : shardingSeed;
    return Math.floorMod(seed.hashCode(), shardCount);
  }

  private WindowRange resolveWindow(long windowMillis, OffsetDateTime now) {
    long nowMillis = now.toInstant().toEpochMilli();
    long startMillis = Math.floorDiv(nowMillis, windowMillis) * windowMillis;
    OffsetDateTime startAt =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneOffset.UTC);
    OffsetDateTime endAt = startAt.plus(Duration.ofMillis(windowMillis));
    return new WindowRange(startAt, endAt);
  }

  private long retryAfterMillis(OffsetDateTime windowEndAt) {
    long millis = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), windowEndAt).toMillis();
    return Math.max(millis, 0L);
  }

  private String bucketKey(
      RateLimitRuntimeRule rule, String limitKeyHash, OffsetDateTime windowStartAt) {
    return rule.rateLimitRuleId()
        + ":"
        + rule.releaseId()
        + ":"
        + limitKeyHash
        + ":"
        + windowStartAt.toInstant().toEpochMilli();
  }

  private UUID bucketId(
      RateLimitRuntimeRule rule, String limitKeyHash, OffsetDateTime windowStartAt) {
    return UUID.nameUUIDFromBytes(
        bucketKey(rule, limitKeyHash, windowStartAt).getBytes(StandardCharsets.UTF_8));
  }

  private record WindowRange(OffsetDateTime startAt, OffsetDateTime endAt) {}

  private static final class LocalBucket {

    private final UUID id;
    private final OffsetDateTime windowStartAt;
    private final OffsetDateTime windowEndAt;
    private final OffsetDateTime resetAt;
    private final OffsetDateTime expiresAt;
    private final long quota;
    private long usedPermits;
    private long remainingPermits;

    private LocalBucket(
        UUID id,
        OffsetDateTime windowStartAt,
        OffsetDateTime windowEndAt,
        OffsetDateTime resetAt,
        OffsetDateTime expiresAt,
        long quota,
        long usedPermits,
        long remainingPermits) {
      this.id = id;
      this.windowStartAt = windowStartAt;
      this.windowEndAt = windowEndAt;
      this.resetAt = resetAt;
      this.expiresAt = expiresAt;
      this.quota = quota;
      this.usedPermits = usedPermits;
      this.remainingPermits = remainingPermits;
    }
  }
}

package io.github.stellorbit.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LocalRateLimitCounterStoreLoadTest {

  @Test
  void shouldEvaluateHighVolumeRequestsWithoutDatabaseAccess() {
    RuntimeRateLimitProperties properties = new RuntimeRateLimitProperties();
    LocalRateLimitCounterStore counterStore = new LocalRateLimitCounterStore(properties);
    RateLimitRuntimeRule rule =
        new RateLimitRuntimeRule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "llm-token-limit",
            "LLM Token Limit",
            "FIXED_WINDOW",
            "GLOBAL_QUOTA",
            1_000_000L,
            1_000L,
            16,
            "FAIL_CLOSED",
            Map.of(),
            Map.of(),
            Map.of("quotaMetric", "TOTAL_TOKENS"),
            OffsetDateTime.now(ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC));

    long started = System.nanoTime();
    long allowed =
        IntStream.range(0, 100_000)
            .parallel()
            .mapToObj(
                index ->
                    counterStore.evaluate(
                        rule,
                        "hot-key",
                        "request-" + ThreadLocalRandom.current().nextInt(10_000),
                        1L,
                        OffsetDateTime.now(ZoneOffset.UTC)))
            .filter(LocalRateLimitDecision::allowed)
            .count();
    long elapsedNanos = System.nanoTime() - started;

    assertThat(allowed).isPositive();
    assertThat(counterStore.bucketCount()).isGreaterThan(0);
    System.out.printf(
        "local rate limit load test: requests=%d, allowed=%d, elapsedMs=%d%n",
        100_000, allowed, elapsedNanos / 1_000_000);
  }
}

package io.github.stellorbit.api;

import io.github.stellorbit.api.dto.DistributedRateLimitRuleSnapshotResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleWatchEventResponse;
import io.github.stellorbit.application.usecase.DistributedRateLimitRuleRuntimeUseCase;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stellorbit/runtime/distributed-rate-limits")
public class DistributedRateLimitRuleRuntimeController {

  private static final long WATCH_TIMEOUT_MILLIS = 30_000L;

  private final DistributedRateLimitRuleRuntimeUseCase distributedRateLimitRuleRuntimeUseCase;

  public DistributedRateLimitRuleRuntimeController(
      DistributedRateLimitRuleRuntimeUseCase distributedRateLimitRuleRuntimeUseCase) {
    this.distributedRateLimitRuleRuntimeUseCase = distributedRateLimitRuleRuntimeUseCase;
  }

  /** 分页获取所有应用的分布式限流规则快照，响应中的content字段与配置中心下发内容一致。 */
  @GetMapping("/snapshot")
  public DistributedRateLimitRuleSnapshotResponse snapshot(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
    return distributedRateLimitRuleRuntimeUseCase.snapshot(page, size);
  }

  /** 查询分布式限流规则全量内存缓存指标，用于确认本实例的Caffeine缓存状态。 */
  @GetMapping("/cache/telemetry")
  public Map<String, Object> cacheTelemetry() {
    return distributedRateLimitRuleRuntimeUseCase.cacheTelemetry();
  }

  /** 监听分布式限流规则快照变更，发生变化时通过SSE返回完整内存快照。 */
  @GetMapping(value = "/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter watch(
      @RequestParam(required = false) Long currentSnapshotVersion,
      @RequestParam(required = false) String currentChecksum) {
    SseEmitter emitter = new SseEmitter(WATCH_TIMEOUT_MILLIS);
    CompletableFuture.runAsync(
        () -> streamWatchEvents(emitter, currentSnapshotVersion, currentChecksum));
    return emitter;
  }

  private void streamWatchEvents(
      SseEmitter emitter, Long currentSnapshotVersion, String currentChecksum) {
    Long seenSnapshotVersion = currentSnapshotVersion;
    String seenChecksum = currentChecksum;
    try {
      for (int index = 0; index < 30; index++) {
        if (distributedRateLimitRuleRuntimeUseCase.changedSince(
            seenSnapshotVersion, seenChecksum)) {
          DistributedRateLimitRuleSnapshotResponse snapshot =
              distributedRateLimitRuleRuntimeUseCase.fullSnapshot();
          DistributedRateLimitRuleWatchEventResponse event =
              new DistributedRateLimitRuleWatchEventResponse(
                  "distributed-rate-limit:"
                      + snapshot.snapshotVersion()
                      + ":"
                      + snapshot.checksum(),
                  "SNAPSHOT_CHANGED",
                  seenSnapshotVersion,
                  snapshot.snapshotVersion(),
                  snapshot.checksum(),
                  OffsetDateTime.now(),
                  snapshot);
          emitter.send(SseEmitter.event().id(event.eventId()).name("snapshot").data(event));
          seenSnapshotVersion = snapshot.snapshotVersion();
          seenChecksum = snapshot.checksum();
        } else {
          OffsetDateTime generatedAt = OffsetDateTime.now();
          DistributedRateLimitRuleWatchEventResponse event =
              new DistributedRateLimitRuleWatchEventResponse(
                  "distributed-rate-limit:heartbeat:" + generatedAt.toInstant().toEpochMilli(),
                  "WATCH_HEARTBEAT",
                  seenSnapshotVersion,
                  distributedRateLimitRuleRuntimeUseCase.latestSnapshotVersion(),
                  distributedRateLimitRuleRuntimeUseCase.latestChecksum(),
                  generatedAt,
                  null);
          emitter.send(SseEmitter.event().name("heartbeat").data(event));
        }
        Thread.sleep(1_000L);
      }
      emitter.complete();
    } catch (IOException exception) {
      emitter.completeWithError(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      emitter.completeWithError(exception);
    } catch (RuntimeException exception) {
      emitter.completeWithError(exception);
    }
  }
}

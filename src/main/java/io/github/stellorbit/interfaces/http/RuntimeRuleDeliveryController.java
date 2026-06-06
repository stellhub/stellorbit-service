package io.github.stellorbit.interfaces.http;

import io.github.stellorbit.application.usecase.RuntimeRuleDeliveryUseCase;
import io.github.stellorbit.interfaces.http.dto.ClientHeartbeatRequest;
import io.github.stellorbit.interfaces.http.dto.ClientHeartbeatResponse;
import io.github.stellorbit.interfaces.http.dto.ClientInstanceViewResponse;
import io.github.stellorbit.interfaces.http.dto.ClientRuleAckRequest;
import io.github.stellorbit.interfaces.http.dto.ClientRuleAckResponse;
import io.github.stellorbit.interfaces.http.dto.ClientRuleStatusReportRequest;
import io.github.stellorbit.interfaces.http.dto.ClientRuleStatusReportResponse;
import io.github.stellorbit.interfaces.http.dto.ClientVersionNegotiationRequest;
import io.github.stellorbit.interfaces.http.dto.ClientVersionNegotiationResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeRuleSnapshotResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeRuleWatchEventResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stellorbit/runtime/rules")
public class RuntimeRuleDeliveryController {

  private static final long WATCH_TIMEOUT_MILLIS = 30_000L;

  private final RuntimeRuleDeliveryUseCase runtimeRuleDeliveryUseCase;

  public RuntimeRuleDeliveryController(RuntimeRuleDeliveryUseCase runtimeRuleDeliveryUseCase) {
    this.runtimeRuleDeliveryUseCase = runtimeRuleDeliveryUseCase;
  }

  /** 协商客户端运行时规则协议和快照格式。 */
  @PostMapping("/negotiate")
  public ClientVersionNegotiationResponse negotiate(
      @Valid @RequestBody ClientVersionNegotiationRequest request) {
    return runtimeRuleDeliveryUseCase.negotiate(request);
  }

  /** 拉取客户端可见的运行时规则快照。 */
  @GetMapping("/snapshot")
  public RuntimeRuleSnapshotResponse snapshot(
      @RequestParam UUID instanceSpaceId,
      @RequestParam UUID applicationId,
      @RequestParam String clientId,
      @RequestParam(defaultValue = "unknown") String clientVersion,
      @RequestParam(required = false) String acceptedRuntimeFormats,
      @RequestParam(required = false) Long currentReleaseVersion,
      @RequestParam Map<String, String> requestParams) {
    return runtimeRuleDeliveryUseCase.snapshot(
        instanceSpaceId,
        applicationId,
        clientId,
        clientVersion,
        parseCsv(acceptedRuntimeFormats),
        currentReleaseVersion,
        parseLabels(requestParams));
  }

  /** 监听运行时规则快照变化，首版使用SSE长轮询协议。 */
  @GetMapping(value = "/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter watch(
      @RequestParam UUID instanceSpaceId,
      @RequestParam UUID applicationId,
      @RequestParam String clientId,
      @RequestParam(defaultValue = "unknown") String clientVersion,
      @RequestParam(required = false) String acceptedRuntimeFormats,
      @RequestParam(required = false) Long currentReleaseVersion,
      @RequestParam Map<String, String> requestParams) {
    SseEmitter emitter = new SseEmitter(WATCH_TIMEOUT_MILLIS);
    List<String> formats = parseCsv(acceptedRuntimeFormats);
    Map<String, Object> labels = parseLabels(requestParams);
    CompletableFuture.runAsync(
        () ->
            streamWatchEvents(
                emitter,
                instanceSpaceId,
                applicationId,
                clientId,
                clientVersion,
                formats,
                currentReleaseVersion,
                labels));
    return emitter;
  }

  /** 记录客户端规则快照ACK。 */
  @PostMapping("/acks")
  public ClientRuleAckResponse ack(@Valid @RequestBody ClientRuleAckRequest request) {
    return runtimeRuleDeliveryUseCase.ack(request);
  }

  /** 记录客户端规则实际生效状态。 */
  @PostMapping("/status-reports")
  public ClientRuleStatusReportResponse statusReport(
      @Valid @RequestBody ClientRuleStatusReportRequest request) {
    return runtimeRuleDeliveryUseCase.reportStatus(request);
  }

  /** 记录客户端心跳并返回最新发布水位和限流节点目录。 */
  @PostMapping("/heartbeats")
  public ClientHeartbeatResponse heartbeat(@Valid @RequestBody ClientHeartbeatRequest request) {
    return runtimeRuleDeliveryUseCase.heartbeat(request);
  }

  /** 查询应用下的客户端实例视图。 */
  @GetMapping("/instances")
  public ClientInstanceViewResponse instances(
      @RequestParam UUID instanceSpaceId, @RequestParam UUID applicationId) {
    return runtimeRuleDeliveryUseCase.instanceView(instanceSpaceId, applicationId);
  }

  private void streamWatchEvents(
      SseEmitter emitter,
      UUID instanceSpaceId,
      UUID applicationId,
      String clientId,
      String clientVersion,
      List<String> acceptedRuntimeFormats,
      Long currentReleaseVersion,
      Map<String, Object> labels) {
    Long seenReleaseVersion = currentReleaseVersion;
    try {
      for (int index = 0; index < 30; index++) {
        RuntimeRuleSnapshotResponse snapshot =
            runtimeRuleDeliveryUseCase.snapshot(
                instanceSpaceId,
                applicationId,
                clientId,
                clientVersion,
                acceptedRuntimeFormats,
                seenReleaseVersion,
                labels);
        if (Boolean.TRUE.equals(snapshot.changed())) {
          RuntimeRuleWatchEventResponse event =
              new RuntimeRuleWatchEventResponse(
                  snapshot.releaseId() + ":" + snapshot.releaseVersion(),
                  "SNAPSHOT_CHANGED",
                  seenReleaseVersion,
                  snapshot.releaseVersion(),
                  OffsetDateTime.now(),
                  snapshot);
          emitter.send(SseEmitter.event().id(event.eventId()).name("snapshot").data(event));
          seenReleaseVersion = snapshot.releaseVersion();
        } else {
          Map<String, Object> heartbeatEvent = new LinkedHashMap<>();
          heartbeatEvent.put("eventType", "WATCH_HEARTBEAT");
          heartbeatEvent.put("currentReleaseVersion", seenReleaseVersion);
          heartbeatEvent.put("generatedAt", OffsetDateTime.now());
          emitter.send(SseEmitter.event().name("heartbeat").data(heartbeatEvent));
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

  private List<String> parseCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(part -> !part.isBlank())
        .toList();
  }

  private Map<String, Object> parseLabels(Map<String, String> requestParams) {
    Map<String, Object> labels = new LinkedHashMap<>();
    requestParams.forEach(
        (key, value) -> {
          if (key.startsWith("label.")) {
            labels.put(key.substring("label.".length()), value);
          }
        });
    return labels;
  }
}

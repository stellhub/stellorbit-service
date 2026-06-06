package io.github.stellorbit.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellorbit.application.runtime.RuntimeNodeDirectory;
import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeAckEntity;
import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeSessionEntity;
import io.github.stellorbit.infrastructure.persistence.entity.ClientRuntimeStatusReportEntity;
import io.github.stellorbit.infrastructure.persistence.entity.ReleaseItemEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ClientRuntimeAckRepository;
import io.github.stellorbit.infrastructure.persistence.repository.ClientRuntimeSessionRepository;
import io.github.stellorbit.infrastructure.persistence.repository.ClientRuntimeStatusReportRepository;
import io.github.stellorbit.infrastructure.persistence.repository.ReleaseItemRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleReleaseRepository;
import io.github.stellorbit.interfaces.http.dto.ClientHeartbeatRequest;
import io.github.stellorbit.interfaces.http.dto.ClientHeartbeatResponse;
import io.github.stellorbit.interfaces.http.dto.ClientInstanceViewResponse;
import io.github.stellorbit.interfaces.http.dto.ClientRuleAckRequest;
import io.github.stellorbit.interfaces.http.dto.ClientRuleAckResponse;
import io.github.stellorbit.interfaces.http.dto.ClientRuleStatusReportRequest;
import io.github.stellorbit.interfaces.http.dto.ClientRuleStatusReportResponse;
import io.github.stellorbit.interfaces.http.dto.ClientRuntimeInstanceResponse;
import io.github.stellorbit.interfaces.http.dto.ClientVersionNegotiationRequest;
import io.github.stellorbit.interfaces.http.dto.ClientVersionNegotiationResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeNodeDirectoryResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeRuleSnapshotResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeSnapshotRuleResponse;
import io.github.stellorbit.interfaces.http.error.InvalidRuleRequestException;
import io.github.stellorbit.interfaces.http.error.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeRuleDeliveryUseCase {

  private static final String PROTOCOL_VERSION = "stellorbit.runtime.protocol.v1";
  private static final String SNAPSHOT_SCHEMA_VERSION = "stellorbit.runtime.snapshot.v1";
  private static final String COMPATIBILITY_MODE = "JSON_CANONICAL_PROTOBUF_OPTIONAL";
  private static final long HEARTBEAT_TTL_MILLIS = 30_000L;
  private static final List<String> DELIVERABLE_RELEASE_STATUSES = List.of("PUBLISHED");

  private final RuleReleaseRepository ruleReleaseRepository;
  private final ReleaseItemRepository releaseItemRepository;
  private final ClientRuntimeSessionRepository sessionRepository;
  private final ClientRuntimeAckRepository ackRepository;
  private final ClientRuntimeStatusReportRepository statusReportRepository;
  private final RuntimeNodeDirectory runtimeNodeDirectory;
  private final ObjectMapper objectMapper;

  public RuntimeRuleDeliveryUseCase(
      RuleReleaseRepository ruleReleaseRepository,
      ReleaseItemRepository releaseItemRepository,
      ClientRuntimeSessionRepository sessionRepository,
      ClientRuntimeAckRepository ackRepository,
      ClientRuntimeStatusReportRepository statusReportRepository,
      RuntimeNodeDirectory runtimeNodeDirectory,
      ObjectMapper objectMapper) {
    this.ruleReleaseRepository = ruleReleaseRepository;
    this.releaseItemRepository = releaseItemRepository;
    this.sessionRepository = sessionRepository;
    this.ackRepository = ackRepository;
    this.statusReportRepository = statusReportRepository;
    this.runtimeNodeDirectory = runtimeNodeDirectory;
    this.objectMapper = objectMapper;
  }

  /** 协商客户端运行时规则协议版本和快照格式。 */
  @Transactional
  public ClientVersionNegotiationResponse negotiate(ClientVersionNegotiationRequest request) {
    if (!supportsProtocol(request.supportedProtocolVersions())) {
      throw new InvalidRuleRequestException("客户端不支持当前Stellorbit运行时协议版本");
    }
    String runtimeFormat = negotiateRuntimeFormat(request.acceptedRuntimeFormats());
    RuntimeNodeDirectoryResponse directory = runtimeNodeDirectory.currentDirectory();
    upsertSession(
        request.instanceSpaceId(),
        request.applicationId(),
        request.clientId(),
        request.clientVersion(),
        PROTOCOL_VERSION,
        SNAPSHOT_SCHEMA_VERSION,
        runtimeFormat,
        null,
        null,
        null,
        null,
        request.labels(),
        request.metadata(),
        directory.ringVersion());
    return new ClientVersionNegotiationResponse(
        PROTOCOL_VERSION,
        SNAPSHOT_SCHEMA_VERSION,
        runtimeFormat,
        COMPATIBILITY_MODE,
        "/api/stellorbit/runtime/rules/snapshot",
        "/api/stellorbit/runtime/rules/watch",
        "/api/stellorbit/runtime/rules/acks",
        "/api/stellorbit/runtime/rules/status-reports",
        "/api/stellorbit/runtime/rules/heartbeats",
        protobufCompatibility(),
        directory,
        OffsetDateTime.now());
  }

  /** 拉取客户端可见的最新运行时规则快照。 */
  @Transactional(readOnly = true)
  public RuntimeRuleSnapshotResponse snapshot(
      UUID instanceSpaceId,
      UUID applicationId,
      String clientId,
      String clientVersion,
      List<String> acceptedRuntimeFormats,
      Long currentReleaseVersion,
      Map<String, Object> labels) {
    String runtimeFormat = negotiateRuntimeFormat(acceptedRuntimeFormats);
    Optional<RuleReleaseEntity> visibleRelease =
        visibleRelease(instanceSpaceId, applicationId, clientId, labels);
    if (visibleRelease.isEmpty()) {
      return emptySnapshot(runtimeFormat, currentReleaseVersion, Boolean.FALSE);
    }
    RuleReleaseEntity release = visibleRelease.get();
    boolean changed = !Objects.equals(currentReleaseVersion, release.getReleaseVersion());
    List<ReleaseItemEntity> items =
        changed ? releaseItemRepository.findByReleaseId(release.getId()) : List.of();
    return toSnapshotResponse(release, runtimeFormat, changed, Boolean.TRUE, items);
  }

  /** 记录客户端对快照的ACK。 */
  @Transactional
  public ClientRuleAckResponse ack(ClientRuleAckRequest request) {
    validateRuntimeFormat(request.runtimeFormat());
    validateProtocol(request.protocolVersion(), request.snapshotSchemaVersion());
    RuleReleaseEntity release = getRelease(request.releaseId());
    ensureReleaseTarget(
        request.instanceSpaceId(), request.applicationId(), request.releaseVersion(), release);
    ClientRuntimeAckEntity entity =
        ackRepository
            .findByReleaseIdAndClientId(request.releaseId(), request.clientId())
            .orElseGet(ClientRuntimeAckEntity::new);
    entity.setInstanceSpaceId(request.instanceSpaceId());
    entity.setApplicationId(request.applicationId());
    entity.setReleaseId(request.releaseId());
    entity.setReleaseVersion(request.releaseVersion());
    entity.setClientId(request.clientId());
    entity.setClientVersion(request.clientVersion());
    entity.setProtocolVersion(request.protocolVersion());
    entity.setSnapshotSchemaVersion(request.snapshotSchemaVersion());
    entity.setRuntimeFormat(normalizeFormat(request.runtimeFormat()));
    entity.setAckStatus(normalizeStatus(request.ackStatus(), "ACK"));
    entity.setChecksum(request.checksum());
    entity.setMessage(request.message());
    entity.setAppliedAt(request.appliedAt());
    ClientRuntimeAckEntity saved = ackRepository.save(entity);
    return new ClientRuleAckResponse(
        saved.getId(),
        Boolean.TRUE,
        latestReleaseVersion(request.instanceSpaceId(), request.applicationId()),
        OffsetDateTime.now());
  }

  /** 记录客户端规则实际生效状态。 */
  @Transactional
  public ClientRuleStatusReportResponse reportStatus(ClientRuleStatusReportRequest request) {
    validateRuntimeFormat(request.runtimeFormat());
    validateProtocol(request.protocolVersion(), request.snapshotSchemaVersion());
    ClientRuntimeStatusReportEntity entity = new ClientRuntimeStatusReportEntity();
    entity.setInstanceSpaceId(request.instanceSpaceId());
    entity.setApplicationId(request.applicationId());
    entity.setReleaseId(request.releaseId());
    entity.setReleaseVersion(request.releaseVersion());
    entity.setClientId(request.clientId());
    entity.setClientVersion(request.clientVersion());
    entity.setProtocolVersion(request.protocolVersion());
    entity.setSnapshotSchemaVersion(request.snapshotSchemaVersion());
    entity.setRuntimeFormat(normalizeFormat(request.runtimeFormat()));
    entity.setEffectiveStatus(normalizeStatus(request.effectiveStatus(), "STATUS"));
    entity.setRuleStatuses(request.ruleStatuses() == null ? List.of() : request.ruleStatuses());
    entity.setErrorDetails(request.errorDetails() == null ? List.of() : request.errorDetails());
    entity.setReportedAt(
        request.reportedAt() == null ? OffsetDateTime.now() : request.reportedAt());
    ClientRuntimeStatusReportEntity saved = statusReportRepository.save(entity);
    return new ClientRuleStatusReportResponse(
        saved.getId(),
        Boolean.TRUE,
        latestReleaseVersion(request.instanceSpaceId(), request.applicationId()),
        OffsetDateTime.now());
  }

  /** 处理客户端心跳并返回实例视图相关水位。 */
  @Transactional
  public ClientHeartbeatResponse heartbeat(ClientHeartbeatRequest request) {
    validateRuntimeFormat(request.runtimeFormat());
    validateProtocol(request.protocolVersion(), request.snapshotSchemaVersion());
    RuntimeNodeDirectoryResponse directory = runtimeNodeDirectory.currentDirectory();
    ClientRuntimeSessionEntity session =
        upsertSession(
            request.instanceSpaceId(),
            request.applicationId(),
            request.clientId(),
            request.clientVersion(),
            request.protocolVersion(),
            request.snapshotSchemaVersion(),
            normalizeFormat(request.runtimeFormat()),
            request.currentReleaseId(),
            request.currentReleaseVersion(),
            request.clientAddress(),
            request.zone(),
            request.labels(),
            request.metadata(),
            directory.ringVersion());
    Long latestReleaseVersion =
        latestReleaseVersion(
            request.instanceSpaceId(),
            request.applicationId(),
            request.clientId(),
            request.labels());
    boolean needRefresh =
        latestReleaseVersion != null
            && !Objects.equals(latestReleaseVersion, request.currentReleaseVersion());
    return new ClientHeartbeatResponse(
        session.getId(),
        OffsetDateTime.now(),
        HEARTBEAT_TTL_MILLIS,
        latestReleaseVersion,
        needRefresh,
        directory);
  }

  /** 查询当前应用的客户端实例视图。 */
  @Transactional(readOnly = true)
  public ClientInstanceViewResponse instanceView(UUID instanceSpaceId, UUID applicationId) {
    RuntimeNodeDirectoryResponse directory = runtimeNodeDirectory.currentDirectory();
    OffsetDateTime now = OffsetDateTime.now();
    List<ClientRuntimeInstanceResponse> instances =
        sessionRepository
            .findByInstanceSpaceIdAndApplicationIdOrderByLastHeartbeatAtDesc(
                instanceSpaceId, applicationId)
            .stream()
            .map(session -> toInstanceResponse(session, now))
            .toList();
    return new ClientInstanceViewResponse(
        instanceSpaceId, applicationId, now, directory, instances);
  }

  public String protocolVersion() {
    return PROTOCOL_VERSION;
  }

  public String snapshotSchemaVersion() {
    return SNAPSHOT_SCHEMA_VERSION;
  }

  private ClientRuntimeSessionEntity upsertSession(
      UUID instanceSpaceId,
      UUID applicationId,
      String clientId,
      String clientVersion,
      String protocolVersion,
      String snapshotSchemaVersion,
      String runtimeFormat,
      UUID currentReleaseId,
      Long currentReleaseVersion,
      String clientAddress,
      String zone,
      Map<String, Object> labels,
      Map<String, Object> metadata,
      String ringVersion) {
    ClientRuntimeSessionEntity entity =
        sessionRepository
            .findByInstanceSpaceIdAndApplicationIdAndClientId(
                instanceSpaceId, applicationId, clientId)
            .orElseGet(ClientRuntimeSessionEntity::new);
    entity.setInstanceSpaceId(instanceSpaceId);
    entity.setApplicationId(applicationId);
    entity.setClientId(clientId);
    entity.setClientVersion(clientVersion);
    entity.setProtocolVersion(protocolVersion);
    entity.setSnapshotSchemaVersion(snapshotSchemaVersion);
    entity.setRuntimeFormat(runtimeFormat);
    entity.setCurrentReleaseId(currentReleaseId);
    entity.setCurrentReleaseVersion(currentReleaseVersion);
    entity.setClientAddress(clientAddress);
    entity.setZone(zone);
    entity.setLabels(labels == null ? new LinkedHashMap<>() : labels);
    entity.setMetadata(metadata == null ? new LinkedHashMap<>() : metadata);
    entity.setRateLimitRingVersion(ringVersion);
    entity.setSessionStatus("ONLINE");
    entity.setLastHeartbeatAt(OffsetDateTime.now());
    return sessionRepository.save(entity);
  }

  private Optional<RuleReleaseEntity> visibleRelease(
      UUID instanceSpaceId, UUID applicationId, String clientId, Map<String, Object> labels) {
    List<RuleReleaseEntity> releases =
        ruleReleaseRepository
            .findByInstanceSpaceIdAndApplicationIdAndReleaseStatusInOrderByReleaseVersionDesc(
                instanceSpaceId, applicationId, DELIVERABLE_RELEASE_STATUSES);
    return releases.stream().filter(release -> grayMatched(release, clientId, labels)).findFirst();
  }

  private RuntimeRuleSnapshotResponse toSnapshotResponse(
      RuleReleaseEntity release,
      String requestedRuntimeFormat,
      Boolean changed,
      Boolean grayMatched,
      List<ReleaseItemEntity> items) {
    RuntimeNodeDirectoryResponse directory = runtimeNodeDirectory.currentDirectory();
    String deliveryFormat = compatibleDeliveryFormat(requestedRuntimeFormat, release);
    return new RuntimeRuleSnapshotResponse(
        release.getId(),
        release.getReleaseVersion(),
        release.getReleaseStatus(),
        PROTOCOL_VERSION,
        SNAPSHOT_SCHEMA_VERSION,
        deliveryFormat,
        changed,
        grayMatched,
        COMPATIBILITY_MODE,
        release.getChecksum(),
        release.getPublishedAt(),
        OffsetDateTime.now(),
        "JSON".equals(deliveryFormat) ? release.getReleaseSnapshotJson() : null,
        "PROTOBUF".equals(deliveryFormat)
            ? encodeBytes(release.getReleaseSnapshotBytes(), release.getReleaseSnapshotJson())
            : null,
        items.stream().map(item -> toRuleResponse(item, deliveryFormat)).toList(),
        directory);
  }

  private RuntimeSnapshotRuleResponse toRuleResponse(
      ReleaseItemEntity item, String deliveryFormat) {
    return new RuntimeSnapshotRuleResponse(
        item.getRuleId(),
        item.getRuleType(),
        item.getRuleCode(),
        item.getRuleName(),
        item.getDraftVersion(),
        item.getPriority(),
        item.getChecksum(),
        "JSON".equals(deliveryFormat) ? item.getRuntimeSnapshotJson() : null,
        "PROTOBUF".equals(deliveryFormat)
            ? encodeBytes(item.getRuntimeSnapshotBytes(), item.getRuntimeSnapshotJson())
            : null);
  }

  private RuntimeRuleSnapshotResponse emptySnapshot(
      String runtimeFormat, Long currentReleaseVersion, Boolean grayMatched) {
    return new RuntimeRuleSnapshotResponse(
        null,
        currentReleaseVersion,
        null,
        PROTOCOL_VERSION,
        SNAPSHOT_SCHEMA_VERSION,
        runtimeFormat,
        Boolean.FALSE,
        grayMatched,
        COMPATIBILITY_MODE,
        null,
        null,
        OffsetDateTime.now(),
        null,
        null,
        List.of(),
        runtimeNodeDirectory.currentDirectory());
  }

  private String compatibleDeliveryFormat(
      String requestedRuntimeFormat, RuleReleaseEntity release) {
    String releaseFormat = normalizeFormat(release.getRuntimeFormat());
    if (releaseFormat.equals(requestedRuntimeFormat)) {
      return requestedRuntimeFormat;
    }
    if ("JSON".equals(requestedRuntimeFormat) && release.getReleaseSnapshotJson() != null) {
      return "JSON";
    }
    if ("PROTOBUF".equals(requestedRuntimeFormat) && release.getReleaseSnapshotBytes() != null) {
      return "PROTOBUF";
    }
    return release.getReleaseSnapshotJson() != null ? "JSON" : "PROTOBUF";
  }

  private boolean grayMatched(
      RuleReleaseEntity release, String clientId, Map<String, Object> labels) {
    Map<String, Object> snapshot = release.getReleaseSnapshotJson();
    if (snapshot == null || !(snapshot.get("grayPolicy") instanceof Map<?, ?> rawPolicy)) {
      return true;
    }
    Map<?, ?> policy = rawPolicy;
    if (Boolean.FALSE.equals(policy.get("enabled"))) {
      return true;
    }
    if (matchesRequiredLabels(policy.get("clientLabels"), labels)
        || matchesRequiredLabels(policy.get("labels"), labels)) {
      return true;
    }
    Object percentage = policy.get("percentage");
    if (percentage instanceof Number number) {
      int value = Math.max(0, Math.min(100, number.intValue()));
      int bucket =
          Math.floorMod(
              (release.getId() + ":" + clientId + ":" + policy.get("hashSeed")).hashCode(), 100);
      return bucket < value;
    }
    return true;
  }

  private boolean matchesRequiredLabels(Object requiredLabels, Map<String, Object> labels) {
    if (!(requiredLabels instanceof Map<?, ?> required) || required.isEmpty()) {
      return false;
    }
    if (labels == null || labels.isEmpty()) {
      return false;
    }
    for (Map.Entry<?, ?> entry : required.entrySet()) {
      Object actual = labels.get(String.valueOf(entry.getKey()));
      if (!Objects.equals(String.valueOf(entry.getValue()), String.valueOf(actual))) {
        return false;
      }
    }
    return true;
  }

  private String negotiateRuntimeFormat(List<String> acceptedRuntimeFormats) {
    if (acceptedRuntimeFormats == null || acceptedRuntimeFormats.isEmpty()) {
      return "JSON";
    }
    List<String> normalized = acceptedRuntimeFormats.stream().map(this::normalizeFormat).toList();
    if (normalized.contains("PROTOBUF")) {
      return "PROTOBUF";
    }
    if (normalized.contains("JSON")) {
      return "JSON";
    }
    throw new InvalidRuleRequestException("客户端不支持JSON或PROTOBUF运行时快照格式");
  }

  private boolean supportsProtocol(List<String> supportedProtocolVersions) {
    return supportedProtocolVersions == null
        || supportedProtocolVersions.isEmpty()
        || supportedProtocolVersions.contains(PROTOCOL_VERSION);
  }

  private RuleReleaseEntity getRelease(UUID releaseId) {
    return ruleReleaseRepository
        .findById(releaseId)
        .orElseThrow(() -> new ResourceNotFoundException("rule release", releaseId));
  }

  private void ensureReleaseTarget(
      UUID instanceSpaceId, UUID applicationId, Long releaseVersion, RuleReleaseEntity release) {
    if (!Objects.equals(instanceSpaceId, release.getInstanceSpaceId())
        || !Objects.equals(applicationId, release.getApplicationId())
        || !Objects.equals(releaseVersion, release.getReleaseVersion())) {
      throw new InvalidRuleRequestException("客户端上报的发布目标与服务端发布记录不一致");
    }
  }

  private Long latestReleaseVersion(UUID instanceSpaceId, UUID applicationId) {
    return latestReleaseVersion(instanceSpaceId, applicationId, "", Map.of());
  }

  private Long latestReleaseVersion(
      UUID instanceSpaceId, UUID applicationId, String clientId, Map<String, Object> labels) {
    return visibleRelease(
            instanceSpaceId, applicationId, clientId, labels == null ? Map.of() : labels)
        .map(RuleReleaseEntity::getReleaseVersion)
        .orElse(null);
  }

  private void validateRuntimeFormat(String runtimeFormat) {
    normalizeFormat(runtimeFormat);
  }

  private void validateProtocol(String protocolVersion, String snapshotSchemaVersion) {
    if (!PROTOCOL_VERSION.equals(protocolVersion)
        || !SNAPSHOT_SCHEMA_VERSION.equals(snapshotSchemaVersion)) {
      throw new InvalidRuleRequestException("客户端运行时协议版本或快照Schema版本不兼容");
    }
  }

  private String normalizeFormat(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!List.of("JSON", "PROTOBUF").contains(normalized)) {
      throw new InvalidRuleRequestException("运行时快照格式只支持JSON或PROTOBUF");
    }
    return normalized;
  }

  private String normalizeStatus(String value, String statusType) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    List<String> allowed =
        "ACK".equals(statusType)
            ? List.of("RECEIVED", "APPLIED", "REJECTED", "ROLLBACK_APPLIED", "STALE")
            : List.of("APPLIED", "PARTIAL_APPLIED", "FAILED", "ROLLING_BACK", "ROLLED_BACK");
    if (!allowed.contains(normalized)) {
      throw new InvalidRuleRequestException(statusType + "状态不合法: " + value);
    }
    return normalized;
  }

  private String encodeBytes(byte[] bytes, Map<String, Object> jsonFallback) {
    byte[] payload = bytes != null ? bytes : jsonFallbackBytes(jsonFallback);
    return Base64.getEncoder().encodeToString(payload);
  }

  private byte[] jsonFallbackBytes(Map<String, Object> jsonFallback) {
    try {
      return objectMapper.writeValueAsBytes(jsonFallback == null ? Map.of() : jsonFallback);
    } catch (JsonProcessingException exception) {
      return String.valueOf(jsonFallback == null ? Map.of() : jsonFallback)
          .getBytes(StandardCharsets.UTF_8);
    }
  }

  private ClientRuntimeInstanceResponse toInstanceResponse(
      ClientRuntimeSessionEntity session, OffsetDateTime now) {
    String status =
        session.getLastHeartbeatAt().plusNanos(HEARTBEAT_TTL_MILLIS * 1_000_000L).isBefore(now)
            ? "STALE"
            : session.getSessionStatus();
    return new ClientRuntimeInstanceResponse(
        session.getId(),
        session.getClientId(),
        session.getClientVersion(),
        session.getProtocolVersion(),
        session.getSnapshotSchemaVersion(),
        session.getRuntimeFormat(),
        session.getCurrentReleaseId(),
        session.getCurrentReleaseVersion(),
        session.getClientAddress(),
        session.getZone(),
        session.getLabels(),
        session.getRateLimitRingVersion(),
        status,
        session.getFirstSeenAt(),
        session.getLastHeartbeatAt());
  }

  private Map<String, Object> protobufCompatibility() {
    Map<String, Object> compatibility = new LinkedHashMap<>();
    compatibility.put("jsonCanonical", Boolean.TRUE);
    compatibility.put("unknownFields", "IGNORE");
    compatibility.put("fieldNumberPolicy", "RESERVED_ON_DELETE");
    compatibility.put("enumPolicy", "APPEND_ONLY");
    compatibility.put("bytesEncoding", "BASE64");
    compatibility.put("schemaRegistry", SNAPSHOT_SCHEMA_VERSION);
    return compatibility;
  }
}

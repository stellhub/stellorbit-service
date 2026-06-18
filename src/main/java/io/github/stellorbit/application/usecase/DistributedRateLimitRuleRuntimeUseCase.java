package io.github.stellorbit.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.github.stellflux.caffeine.StellfluxCaffeineCache;
import io.github.stellflux.caffeine.StellfluxCaffeineCacheFactory;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleConfigChangeResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleConfigResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleDeltaResponse;
import io.github.stellorbit.api.dto.DistributedRateLimitRuleSnapshotResponse;
import io.github.stellorbit.api.dto.PageResponse;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.application.event.RateLimitRuleChangedEvent;
import io.github.stellorbit.application.port.AggregatedGovernanceRuleConfig;
import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleAggregatePayloadBuilder;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.domain.RateLimitRuleModeSupport;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class DistributedRateLimitRuleRuntimeUseCase {

  private static final Logger log =
      LoggerFactory.getLogger(DistributedRateLimitRuleRuntimeUseCase.class);
  private static final String RUNTIME_FORMAT = "JSON";
  private static final String RELEASE_NAME_PREFIX = "distributed-rate-limit-snapshot-";
  private static final int MAX_PAGE_SIZE = 500;
  private static final int MAX_CHANGE_LOG_SIZE = 1024;
  private static final String SNAPSHOT_CACHE_NAME = "stellorbit-distributed-rate-limit-snapshot";
  private static final String SNAPSHOT_CACHE_KEY = "latest";
  private static final String UPSERT_CONFIG = "UPSERT_CONFIG";
  private static final String DELETE_CONFIG = "DELETE_CONFIG";

  private final GovernanceRuleRepository governanceRuleRepository;
  private final RateLimitRuleRepository rateLimitRuleRepository;
  private final ApplicationRepository applicationRepository;
  private final GovernanceRuleContentCompiler governanceRuleContentCompiler;
  private final GovernanceRuleAggregatePayloadBuilder governanceRuleAggregatePayloadBuilder;
  private final ObjectMapper canonicalObjectMapper;
  private final StellfluxCaffeineCache<String, CacheState> cacheStateCache;

  public DistributedRateLimitRuleRuntimeUseCase(
      GovernanceRuleRepository governanceRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository,
      ApplicationRepository applicationRepository,
      GovernanceRuleContentCompiler governanceRuleContentCompiler,
      GovernanceRuleAggregatePayloadBuilder governanceRuleAggregatePayloadBuilder,
      ObjectMapper objectMapper,
      StellfluxCaffeineCacheFactory caffeineCacheFactory) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.rateLimitRuleRepository = rateLimitRuleRepository;
    this.applicationRepository = applicationRepository;
    this.governanceRuleContentCompiler = governanceRuleContentCompiler;
    this.governanceRuleAggregatePayloadBuilder = governanceRuleAggregatePayloadBuilder;
    this.canonicalObjectMapper =
        objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.cacheStateCache =
        caffeineCacheFactory.createCache(SNAPSHOT_CACHE_NAME, caffeine -> caffeine.maximumSize(1L));
    this.cacheStateCache.put(SNAPSHOT_CACHE_KEY, CacheState.empty());
  }

  /** 分页读取分布式限流服务端可消费的全量内存快照。 */
  public DistributedRateLimitRuleSnapshotResponse snapshot(int page, int size) {
    return toSnapshotResponse(currentCacheState(), page, size, true);
  }

  /** 读取分布式限流服务端watch事件使用的完整内存快照。 */
  public DistributedRateLimitRuleSnapshotResponse fullSnapshot() {
    CacheState state = currentCacheState();
    return toSnapshotResponse(state, 0, Math.max(1, state.configs().size()), false);
  }

  /** 判断调用方持有的快照水位是否已经落后。 */
  public boolean changedSince(Long currentSnapshotVersion, String currentChecksum) {
    return requiresFullSync(currentSnapshotVersion, currentChecksum)
        || !Objects.equals(currentSnapshotVersion, latestSnapshotVersion());
  }

  /** 判断当前水位是否无法通过本机增量日志追赶，无法追赶时调用方应重新分页全量同步。 */
  public boolean requiresFullSync(Long currentSnapshotVersion, String currentChecksum) {
    CacheState state = currentCacheState();
    if (currentSnapshotVersion == null) {
      return true;
    }
    if (currentSnapshotVersion < 0 || currentSnapshotVersion > state.snapshotVersion()) {
      return true;
    }
    if (Objects.equals(currentSnapshotVersion, state.snapshotVersion())) {
      return !isBlank(currentChecksum) && !Objects.equals(currentChecksum, state.checksum());
    }
    List<ChangeBatch> batches = changeBatchesAfter(state, currentSnapshotVersion);
    if (batches.isEmpty() || batches.getFirst().fromSnapshotVersion() != currentSnapshotVersion) {
      return true;
    }
    if (!isContinuous(currentSnapshotVersion, state.snapshotVersion(), batches)) {
      return true;
    }
    return !isBlank(currentChecksum)
        && !Objects.equals(currentChecksum, batches.getFirst().fromChecksum());
  }

  /** 从指定快照水位开始读取分布式限流规则配置增量。 */
  public DistributedRateLimitRuleDeltaResponse deltaSince(
      Long currentSnapshotVersion, String currentChecksum) {
    if (requiresFullSync(currentSnapshotVersion, currentChecksum)) {
      throw new InvalidRuleRequestException("当前快照水位无法通过增量日志追赶，请重新执行分页全量同步");
    }
    CacheState state = currentCacheState();
    if (Objects.equals(currentSnapshotVersion, state.snapshotVersion())) {
      return new DistributedRateLimitRuleDeltaResponse(
          state.snapshotVersion(),
          state.snapshotVersion(),
          state.checksum(),
          state.checksum(),
          state.generatedAt(),
          0,
          List.of());
    }
    List<ChangeBatch> batches = changeBatchesAfter(state, currentSnapshotVersion);
    List<DistributedRateLimitRuleConfigChangeResponse> changes =
        batches.stream().flatMap(batch -> batch.changes().stream()).toList();
    return new DistributedRateLimitRuleDeltaResponse(
        currentSnapshotVersion,
        state.snapshotVersion(),
        batches.getFirst().fromChecksum(),
        state.checksum(),
        state.generatedAt(),
        changes.size(),
        changes);
  }

  /** 返回当前内存快照版本。 */
  public Long latestSnapshotVersion() {
    return currentCacheState().snapshotVersion();
  }

  /** 返回当前内存快照校验和。 */
  public String latestChecksum() {
    return currentCacheState().checksum();
  }

  /** 查询分布式限流规则全量内存缓存指标。 */
  public Map<String, Object> cacheTelemetry() {
    CacheState state = currentCacheState();
    Map<String, Object> telemetry = new LinkedHashMap<>(cacheStateCache.telemetrySnapshot());
    telemetry.put("cacheName", cacheStateCache.getCacheName());
    telemetry.put("estimatedSize", cacheStateCache.estimatedSize());
    telemetry.put("stats", cacheStats());
    telemetry.put("snapshotVersion", state.snapshotVersion());
    telemetry.put("snapshotChecksum", state.checksum());
    telemetry.put("totalApplications", state.configs().size());
    telemetry.put("totalRules", state.totalRuleCount());
    telemetry.put("changeLogSize", state.changeLog().size());
    return telemetry;
  }

  /** 从数据库刷新全量分布式限流规则内存映射。 */
  public boolean refreshIfChanged() {
    List<GovernanceRuleEntity> rules =
        governanceRuleRepository.findEnabledDistributedRateLimitRules();
    List<GovernanceRuleEntity> orderedRules = orderedRules(rules);
    List<UUID> ruleIds = orderedRules.stream().map(GovernanceRuleEntity::getId).toList();
    Map<UUID, RateLimitRuleEntity> detailById = loadRateLimitDetails(ruleIds);
    Map<UUID, ApplicationEntity> applicationById = loadApplications(orderedRules);
    Map<String, String> sourceChecksumByConfigId =
        sourceChecksumByConfigId(orderedRules, detailById, applicationById);
    String sourceChecksum = sourceChecksum(sourceChecksumByConfigId);

    CacheState current = currentCacheState();
    if (Objects.equals(sourceChecksum, current.sourceChecksum())) {
      return false;
    }

    long nextVersion = Math.max(1L, current.snapshotVersion() + 1L);
    OffsetDateTime generatedAt = OffsetDateTime.now();
    List<DistributedRateLimitRuleConfigResponse> configs =
        buildDistributedRateLimitConfigs(
            orderedRules,
            applicationById,
            nextVersion,
            generatedAt,
            current,
            sourceChecksumByConfigId);
    Map<String, DistributedRateLimitRuleConfigResponse> configById =
        configs.stream()
            .collect(
                Collectors.toMap(
                    DistributedRateLimitRuleConfigResponse::configId,
                    Function.identity(),
                    (left, right) -> right,
                    LinkedHashMap::new));
    String nextChecksum = snapshotChecksum(configs);
    List<DistributedRateLimitRuleConfigChangeResponse> changes =
        configChanges(current, configById, sourceChecksumByConfigId, nextVersion);
    ChangeBatch changeBatch =
        new ChangeBatch(
            current.snapshotVersion(),
            nextVersion,
            current.checksum(),
            nextChecksum,
            generatedAt,
            List.copyOf(changes));
    CacheState next =
        new CacheState(
            nextVersion,
            sourceChecksum,
            nextChecksum,
            generatedAt,
            configs.stream().mapToInt(DistributedRateLimitRuleConfigResponse::ruleCount).sum(),
            List.copyOf(configs),
            Map.copyOf(configById),
            Map.copyOf(sourceChecksumByConfigId),
            appendChangeLog(current.changeLog(), changeBatch));
    cacheStateCache.put(SNAPSHOT_CACHE_KEY, next);
    log.info(
        "Refreshed distributed rate limit rule snapshot, version={}, applications={}, rules={},"
            + " changes={}",
        next.snapshotVersion(),
        next.configs().size(),
        next.totalRuleCount(),
        changes.size());
    return true;
  }

  /** 应用启动完成后预热分布式限流规则内存映射。 */
  @EventListener(ApplicationReadyEvent.class)
  public void refreshOnApplicationReady() {
    refreshSafely("application ready");
  }

  /** 周期性兜底刷新，保证其它治理服务端实例写入后的规则变更也能被当前实例感知。 */
  @Scheduled(
      fixedDelayString = "${stellorbit.distributed-rate-limit.refresh-delay-millis:3000}",
      initialDelayString = "${stellorbit.distributed-rate-limit.initial-delay-millis:3000}")
  public void refreshPeriodically() {
    refreshSafely("scheduled refresh");
  }

  /** 限流规则本实例写入成功后立即刷新内存映射。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void refreshAfterRateLimitRuleChanged(RateLimitRuleChangedEvent event) {
    refreshSafely("rate limit rule changed: " + event.action());
  }

  private void refreshSafely(String reason) {
    try {
      refreshIfChanged();
    } catch (RuntimeException exception) {
      log.warn("Failed to refresh distributed rate limit rule snapshot on {}", reason, exception);
    }
  }

  private CacheState currentCacheState() {
    return cacheStateCache.get(SNAPSHOT_CACHE_KEY, ignored -> CacheState.empty());
  }

  private Map<String, Object> cacheStats() {
    CacheStats stats = cacheStateCache.stats();
    Map<String, Object> statsView = new LinkedHashMap<>();
    statsView.put("requestCount", stats.requestCount());
    statsView.put("hitCount", stats.hitCount());
    statsView.put("missCount", stats.missCount());
    statsView.put("hitRate", stats.hitRate());
    statsView.put("missRate", stats.missRate());
    statsView.put("loadSuccessCount", stats.loadSuccessCount());
    statsView.put("loadFailureCount", stats.loadFailureCount());
    statsView.put("totalLoadTime", stats.totalLoadTime());
    statsView.put("evictionCount", stats.evictionCount());
    statsView.put("evictionWeight", stats.evictionWeight());
    return statsView;
  }

  private List<ChangeBatch> changeBatchesAfter(CacheState state, long currentSnapshotVersion) {
    return state.changeLog().stream()
        .filter(batch -> batch.toSnapshotVersion() > currentSnapshotVersion)
        .toList();
  }

  private boolean isContinuous(
      long currentSnapshotVersion, long latestSnapshotVersion, List<ChangeBatch> batches) {
    long expectedVersion = currentSnapshotVersion;
    for (ChangeBatch batch : batches) {
      if (batch.fromSnapshotVersion() != expectedVersion) {
        return false;
      }
      expectedVersion = batch.toSnapshotVersion();
    }
    return expectedVersion == latestSnapshotVersion;
  }

  private DistributedRateLimitRuleSnapshotResponse toSnapshotResponse(
      CacheState state, int page, int size, boolean enforceMaxPageSize) {
    validatePage(page, size, enforceMaxPageSize);
    int total = state.configs().size();
    int totalPages = total == 0 ? 0 : (total + size - 1) / size;
    int fromIndex = (int) Math.min((long) page * size, total);
    int toIndex = Math.min(fromIndex + size, total);
    PageResponse<DistributedRateLimitRuleConfigResponse> pageResponse =
        new PageResponse<>(
            state.configs().subList(fromIndex, toIndex), page, size, total, totalPages);
    return new DistributedRateLimitRuleSnapshotResponse(
        state.snapshotVersion(),
        state.checksum(),
        state.generatedAt(),
        total,
        state.totalRuleCount(),
        pageResponse);
  }

  private void validatePage(int page, int size, boolean enforceMaxPageSize) {
    if (page < 0) {
      throw new InvalidRuleRequestException("page不能小于0");
    }
    if (size <= 0) {
      throw new InvalidRuleRequestException("size必须大于0");
    }
    if (enforceMaxPageSize && size > MAX_PAGE_SIZE) {
      throw new InvalidRuleRequestException("size不能大于" + MAX_PAGE_SIZE);
    }
  }

  private List<GovernanceRuleEntity> orderedRules(List<GovernanceRuleEntity> rules) {
    return rules.stream()
        .sorted(
            Comparator.comparing(GovernanceRuleEntity::getInstanceSpaceId)
                .thenComparing(GovernanceRuleEntity::getApplicationId)
                .thenComparing(rule -> defaultInteger(rule.getPriority()))
                .thenComparing(GovernanceRuleEntity::getRuleCode))
        .toList();
  }

  private Map<UUID, RateLimitRuleEntity> loadRateLimitDetails(Collection<UUID> ruleIds) {
    if (ruleIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, RateLimitRuleEntity> detailById = new LinkedHashMap<>();
    rateLimitRuleRepository
        .findAllById(ruleIds)
        .forEach(detail -> detailById.put(detail.getId(), detail));
    for (UUID ruleId : ruleIds) {
      if (!detailById.containsKey(ruleId)) {
        throw new InvalidRuleRequestException("限流规则明细不存在: " + ruleId);
      }
    }
    return detailById;
  }

  private Map<UUID, ApplicationEntity> loadApplications(List<GovernanceRuleEntity> rules) {
    List<UUID> applicationIds =
        rules.stream().map(GovernanceRuleEntity::getApplicationId).distinct().toList();
    if (applicationIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ApplicationEntity> applicationById = new LinkedHashMap<>();
    applicationRepository
        .findAllById(applicationIds)
        .forEach(application -> applicationById.put(application.getId(), application));
    for (UUID applicationId : applicationIds) {
      if (!applicationById.containsKey(applicationId)) {
        throw new InvalidRuleRequestException("应用不存在: " + applicationId);
      }
    }
    return applicationById;
  }

  private Map<String, String> sourceChecksumByConfigId(
      List<GovernanceRuleEntity> rules,
      Map<UUID, RateLimitRuleEntity> detailById,
      Map<UUID, ApplicationEntity> applicationById) {
    Map<UUID, List<GovernanceRuleEntity>> rulesByApplication = rulesByApplication(rules);
    Map<String, String> checksumByConfigId = new LinkedHashMap<>();
    for (Map.Entry<UUID, List<GovernanceRuleEntity>> entry : rulesByApplication.entrySet()) {
      ApplicationEntity application = applicationById.get(entry.getKey());
      List<Map<String, Object>> sourceItems = new ArrayList<>();
      for (GovernanceRuleEntity rule : entry.getValue()) {
        sourceItems.add(sourceItem(rule, detailById.get(rule.getId()), application));
      }
      checksumByConfigId.put(rateLimitConfigId(application), sha256(toJson(sourceItems)));
    }
    return checksumByConfigId;
  }

  private String sourceChecksum(Map<String, String> sourceChecksumByConfigId) {
    return sha256(toJson(sourceChecksumByConfigId));
  }

  private Map<String, Object> sourceItem(
      GovernanceRuleEntity rule, RateLimitRuleEntity detail, ApplicationEntity application) {
    String executionLocation =
        RateLimitRuleModeSupport.normalizeExecutionLocation(
            detail.getExecutionLocation(), detail.getEnforcementMode());
    String coordinationMode =
        RateLimitRuleModeSupport.normalizeCoordinationMode(
            detail.getCoordinationMode(), detail.getEnforcementMode());
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("instanceSpaceId", rule.getInstanceSpaceId());
    item.put("applicationId", rule.getApplicationId());
    item.put("applicationCode", application.getApplicationCode());
    item.put("ruleId", rule.getId());
    item.put("ruleCode", rule.getRuleCode());
    item.put("ruleName", rule.getRuleName());
    item.put("sourceFormat", rule.getSourceFormat());
    item.put("runtimeFormat", rule.getRuntimeFormat());
    item.put("cueSource", rule.getCueSource());
    item.put("runtimeSnapshotJson", rule.getRuntimeSnapshotJson());
    item.put("checksum", rule.getChecksum());
    item.put("priority", rule.getPriority());
    item.put("enabled", rule.getEnabled());
    item.put("status", rule.getStatus());
    item.put("draftVersion", rule.getDraftVersion());
    item.put("updatedAt", text(rule.getUpdatedAt()));
    item.put("rowVersion", rule.getRowVersion());
    item.put("limitType", detail.getLimitType());
    item.put("limitAlgorithm", detail.getLimitAlgorithm());
    item.put("executionLocation", executionLocation);
    item.put("coordinationMode", coordinationMode);
    item.put(
        "enforcementMode",
        RateLimitRuleModeSupport.toLegacyEnforcementMode(executionLocation, coordinationMode));
    item.put("targetSelector", detail.getTargetSelector());
    item.put("dimensions", detail.getDimensions());
    item.put("quotaConfig", detail.getQuotaConfig());
    item.put("windowConfig", detail.getWindowConfig());
    item.put("burstConfig", detail.getBurstConfig());
    item.put("modelLimitConfig", detail.getModelLimitConfig());
    item.put("fallbackPolicy", detail.getFallbackPolicy());
    item.put("responsePolicy", detail.getResponsePolicy());
    item.put("detailUpdatedAt", text(detail.getUpdatedAt()));
    return item;
  }

  private List<DistributedRateLimitRuleConfigResponse> buildDistributedRateLimitConfigs(
      List<GovernanceRuleEntity> rules,
      Map<UUID, ApplicationEntity> applicationById,
      long snapshotVersion,
      OffsetDateTime generatedAt,
      CacheState current,
      Map<String, String> sourceChecksumByConfigId) {
    Map<UUID, List<GovernanceRuleEntity>> rulesByApplication = rulesByApplication(rules);
    List<DistributedRateLimitRuleConfigResponse> configs = new ArrayList<>();
    for (Map.Entry<UUID, List<GovernanceRuleEntity>> entry : rulesByApplication.entrySet()) {
      ApplicationEntity application = applicationById.get(entry.getKey());
      String configId = rateLimitConfigId(application);
      DistributedRateLimitRuleConfigResponse existing = current.configById().get(configId);
      if (existing != null
          && Objects.equals(
              current.sourceChecksumByConfigId().get(configId),
              sourceChecksumByConfigId.get(configId))) {
        configs.add(existing);
        continue;
      }
      List<CompiledGovernanceRule> compiledRules = compileRules(entry.getValue(), application);
      AggregatedGovernanceRuleConfig config =
          governanceRuleAggregatePayloadBuilder
              .build(
                  application,
                  snapshotVersion,
                  RELEASE_NAME_PREFIX + snapshotVersion,
                  RUNTIME_FORMAT,
                  generatedAt,
                  compiledRules)
              .stream()
              .filter(item -> "RATE_LIMIT".equals(item.ruleType()))
              .findFirst()
              .orElseThrow(() -> new InvalidRuleRequestException("限流聚合配置生成失败"));
      if (!config.rules().isEmpty()) {
        configs.add(toConfigResponse(application, config));
      }
    }
    return configs;
  }

  private Map<UUID, List<GovernanceRuleEntity>> rulesByApplication(
      List<GovernanceRuleEntity> rules) {
    return rules.stream()
        .collect(
            Collectors.groupingBy(
                GovernanceRuleEntity::getApplicationId, LinkedHashMap::new, Collectors.toList()));
  }

  private String rateLimitConfigId(ApplicationEntity application) {
    return "stellorbit." + normalize(application.getApplicationCode()) + ".rate_limit";
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
  }

  private List<CompiledGovernanceRule> compileRules(
      List<GovernanceRuleEntity> rules, ApplicationEntity application) {
    List<CompiledGovernanceRule> compiledRules = new ArrayList<>();
    for (GovernanceRuleEntity rule : rules) {
      compiledRules.add(governanceRuleContentCompiler.compile(rule, application));
    }
    return compiledRules;
  }

  private DistributedRateLimitRuleConfigResponse toConfigResponse(
      ApplicationEntity application, AggregatedGovernanceRuleConfig config) {
    return new DistributedRateLimitRuleConfigResponse(
        application.getInstanceSpaceId(),
        application.getId(),
        application.getApplicationCode(),
        config.configId(),
        config.ruleName(),
        config.ruleType(),
        config.stellnulaRuleType(),
        config.publishKind(),
        config.content(),
        config.checksum(),
        config.aggregateChecksum(),
        new LinkedHashMap<>(config.contentModel()),
        config.rules().size());
  }

  private String snapshotChecksum(List<DistributedRateLimitRuleConfigResponse> configs) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (DistributedRateLimitRuleConfigResponse config : configs) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("configId", config.configId());
      item.put("checksum", config.checksum());
      item.put("aggregateChecksum", config.aggregateChecksum());
      item.put("ruleCount", config.ruleCount());
      items.add(item);
    }
    return sha256(toJson(items));
  }

  private List<DistributedRateLimitRuleConfigChangeResponse> configChanges(
      CacheState current,
      Map<String, DistributedRateLimitRuleConfigResponse> nextConfigById,
      Map<String, String> nextSourceChecksumByConfigId,
      long nextVersion) {
    List<DistributedRateLimitRuleConfigChangeResponse> changes = new ArrayList<>();
    for (DistributedRateLimitRuleConfigResponse nextConfig : nextConfigById.values()) {
      DistributedRateLimitRuleConfigResponse previousConfig =
          current.configById().get(nextConfig.configId());
      if (previousConfig == null
          || !Objects.equals(
              current.sourceChecksumByConfigId().get(nextConfig.configId()),
              nextSourceChecksumByConfigId.get(nextConfig.configId()))) {
        changes.add(
            new DistributedRateLimitRuleConfigChangeResponse(
                UPSERT_CONFIG,
                current.snapshotVersion(),
                nextVersion,
                nextConfig.instanceSpaceId(),
                nextConfig.applicationId(),
                nextConfig.applicationCode(),
                nextConfig.configId(),
                previousConfig == null ? null : previousConfig.checksum(),
                nextConfig.checksum(),
                nextConfig));
      }
    }
    for (DistributedRateLimitRuleConfigResponse previousConfig : current.configs()) {
      if (!nextConfigById.containsKey(previousConfig.configId())) {
        changes.add(
            new DistributedRateLimitRuleConfigChangeResponse(
                DELETE_CONFIG,
                current.snapshotVersion(),
                nextVersion,
                previousConfig.instanceSpaceId(),
                previousConfig.applicationId(),
                previousConfig.applicationCode(),
                previousConfig.configId(),
                previousConfig.checksum(),
                null,
                null));
      }
    }
    return changes;
  }

  private List<ChangeBatch> appendChangeLog(List<ChangeBatch> changeLog, ChangeBatch changeBatch) {
    if (changeBatch.changes().isEmpty()) {
      return changeLog;
    }
    List<ChangeBatch> nextChangeLog = new ArrayList<>(changeLog);
    nextChangeLog.add(changeBatch);
    int fromIndex = Math.max(0, nextChangeLog.size() - MAX_CHANGE_LOG_SIZE);
    return List.copyOf(nextChangeLog.subList(fromIndex, nextChangeLog.size()));
  }

  private String toJson(Object value) {
    try {
      return canonicalObjectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("分布式限流规则快照序列化失败: " + exception.getMessage());
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256算法不可用", exception);
    }
  }

  private Integer defaultInteger(Integer value) {
    return value == null ? 1000 : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  private record CacheState(
      long snapshotVersion,
      String sourceChecksum,
      String checksum,
      OffsetDateTime generatedAt,
      int totalRuleCount,
      List<DistributedRateLimitRuleConfigResponse> configs,
      Map<String, DistributedRateLimitRuleConfigResponse> configById,
      Map<String, String> sourceChecksumByConfigId,
      List<ChangeBatch> changeLog) {

    private static CacheState empty() {
      return new CacheState(0L, null, "", null, 0, List.of(), Map.of(), Map.of(), List.of());
    }
  }

  private record ChangeBatch(
      long fromSnapshotVersion,
      long toSnapshotVersion,
      String fromChecksum,
      String toChecksum,
      OffsetDateTime generatedAt,
      List<DistributedRateLimitRuleConfigChangeResponse> changes) {}
}

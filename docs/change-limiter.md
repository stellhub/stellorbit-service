# 分布式限流下游适配修改指南

## 背景

`stellorbit-service` 已将限流规则升级为企业级限流模型。新的模型不再只表达传统 QPS 限流，而是统一覆盖 QPS、并发、Header、热点、自定义、分布式配额、带宽、连接和模型限流。

本次变更会影响所有下游规则使用方：

- `stellhub/stellorbit-java-sdk`：Java 服务治理规则客户端 SDK。
- `stellhub/stellorbit-go-sdk`：Go 服务治理规则客户端 SDK。
- `stellhub/stellpulsar-service`：分布式限流服务端。
- `stellhub/stellpulsar-java-sdk`：Java 分布式限流客户端 SDK。
- `stellhub/stellpulsar-go-sdk`：Go 分布式限流客户端 SDK。

本文档描述这些下游组件应该完成的适配项。

## 统一规则契约

所有下游组件都需要识别新的 `RATE_LIMIT` 规则结构。

核心字段如下：

```json
{
  "limitMode": "QPS | CONCURRENCY | HEADER | HOT_KEY | CUSTOM | QUOTA | BANDWIDTH | CONNECTION | MODEL",
  "limitType": "REQUEST | CONNECTION | BYTE | TENANT | USER | CALLER | API_KEY | RESOURCE | HEADER | GRPC_METADATA | IP | ENDPOINT | METHOD | TOPIC | MODEL_REQUEST | MODEL_TOKEN | MODEL_COST | MODEL_CONCURRENCY | CUSTOM_KEY",
  "limitAlgorithm": "TOKEN_BUCKET | LEAKY_BUCKET | FIXED_WINDOW | SLIDING_WINDOW | QUOTA_LEASE | CONCURRENCY_LIMIT | HOT_KEY | CUSTOM | ADAPTIVE",
  "trafficProtocol": "HTTP | GRPC | TCP | MESSAGE | MODEL | ANY",
  "executionLocation": "APPLICATION | SIDECAR | GATEWAY | EDGE",
  "coordinationMode": "LOCAL_ONLY | GLOBAL_SYNC | GLOBAL_QUOTA",
  "enforcementMode": "LOCAL | GLOBAL_SYNC | GLOBAL_QUOTA | EDGE",
  "targetSelector": {},
  "requestMatcher": {},
  "keyExtractor": {},
  "dimensions": [],
  "quotaConfig": {},
  "windowConfig": {},
  "burstConfig": {},
  "concurrencyConfig": {},
  "hotspotConfig": {},
  "customPolicy": {},
  "modelLimitConfig": {},
  "fallbackPolicy": {},
  "responsePolicy": {},
  "observabilityConfig": {},
  "shadowConfig": {}
}
```

兼容原则：

- `enforcementMode` 只是旧版兼容字段，不再作为事实源。
- 新事实源是 `executionLocation + coordinationMode`。
- 只有 `coordinationMode = GLOBAL_SYNC` 或 `coordinationMode = GLOBAL_QUOTA` 的规则会进入分布式限流链路。
- `coordinationMode = LOCAL_ONLY` 的规则应该由应用侧 SDK、网关、sidecar 或边缘代理本地执行。
- 未识别的 `limitMode`、`limitType`、`limitAlgorithm` 或 `trafficProtocol` 不能静默降级成 QPS，必须进入 last-known-good、fail-open、fail-closed 或明确 unsupported 状态。

## stellorbit-java-sdk

`stellorbit-java-sdk` 的定位仍然是 Java 服务治理规则数据面客户端。它应该完整解析和暴露限流规则，但不应该在 core SDK 中实现具体限流算法。

需要适配：

- 更新 `RateLimitRule` 或等价模型，新增字段：
  - `limitMode`
  - `trafficProtocol`
  - `requestMatcher`
  - `keyExtractor`
  - `concurrencyConfig`
  - `hotspotConfig`
  - `customPolicy`
  - `observabilityConfig`
  - `shadowConfig`
- `RateLimitRuleProvider` 查询能力从 `limitType + targetService` 扩展到：
  - `limitMode`
  - `trafficProtocol`
  - `executionLocation`
  - `coordinationMode`
  - `keyExtractor.keys.source`
- 增加 `isDistributedRule()`：
  - `coordinationMode == GLOBAL_SYNC || coordinationMode == GLOBAL_QUOTA`
- 增加 `isLocalRuntimeRule()`：
  - `coordinationMode == LOCAL_ONLY`
- 对 `HEADER` 规则提供 helper：
  - HTTP Header 使用 `source = HEADER`。
  - gRPC Metadata 使用 `source = GRPC_METADATA`。
- 对 `CUSTOM`、`MODEL`、`HOT_KEY` 不要在 core SDK 内部执行，只保留结构化模型给框架适配层消费。

建议测试：

- 能解析包含全部新增字段的 `RATE_LIMIT` JSON。
- 旧 JSON 缺少 `limitMode` 时可兼容为 `QPS`。
- 新枚举能进入本地 registry。
- distributed filter 只返回 `GLOBAL_SYNC` 和 `GLOBAL_QUOTA`。
- 未识别模式不会污染 last-known-good registry。

## stellorbit-go-sdk

`stellorbit-go-sdk` 应与 Java SDK 保持同构，作为 Go 应用、网关和中间件适配层的治理规则数据源。

需要适配：

- 更新 Go 结构体：
  - `LimitMode string`
  - `TrafficProtocol string`
  - `RequestMatcher map[string]any`
  - `KeyExtractor KeyExtractor`
  - `ConcurrencyConfig map[string]any`
  - `HotspotConfig map[string]any`
  - `CustomPolicy map[string]any`
  - `ObservabilityConfig map[string]any`
  - `ShadowConfig map[string]any`
- 定义枚举常量，避免业务方直接拼字符串。
- `RateLimitRuleProvider` 增加过滤能力：
  - `ByLimitMode`
  - `ByProtocol`
  - `DistributedOnly`
  - `LocalOnly`
- Go SDK 需要保持宽松 JSON 解析：
  - 新字段缺失时使用零值或空 map。
  - 未知字段需要保留或忽略，但不能 panic。
- `keyExtractor.keys` 建议建模为显式结构体，至少包含：
  - `Name`
  - `Source`
  - `Key`
  - `Required`
  - `Normalize`

建议测试：

- `json.Unmarshal` 能解析完整企业级限流规则。
- 老 JSON 没有 `limitMode` 时默认兼容为 `QPS`。
- `coordinationMode` 为空但 `enforcementMode = GLOBAL_QUOTA` 时仍可兼容为分布式规则。
- `keyExtractor.keys` 中未知 source 会被标记为 unsupported。

## stellpulsar-service

`stellpulsar-service` 是分布式限流服务端，只应该处理需要全局协调的规则。

需要适配：

- Rule Sync 只接收：
  - `coordinationMode = GLOBAL_SYNC`
  - `coordinationMode = GLOBAL_QUOTA`
- 启动时通过分页接口拉取全量分布式限流规则。
- 启动后通过 watch 或 delta 接口增量更新规则。
- 不允许每次变更都重新拉全量，避免规则量大时把 `stellorbit-service` 拉爆。
- 内存模型需要升级：
  - 快照中保存完整分布式限流规则。
  - checksum 必须包含新增字段。
  - key 建议使用 `applicationCode + ruleId + limitMode + keyExtractorDigest`。
- Quota Engine 按 `limitMode` 分派：
  - `QPS`：令牌桶、固定窗口或滑动窗口。
  - `QUOTA`：全局配额租约，必须使用 `GLOBAL_QUOTA`。
  - `HEADER`：消费已提取的 quota key，或按 `keyExtractor` 从请求上下文提取。
  - `CONCURRENCY`：需要 acquire/release 生命周期。
  - `CONNECTION`：需要连接级 acquire/release 生命周期。
  - `BANDWIDTH`：`cost` 应支持字节数。
  - `MODEL`：`cost` 应支持 request、token、cost、concurrency 等单位。
  - `HOT_KEY`：需要热点统计、TopN 和动态阈值状态。
  - `CUSTOM`：初期建议只支持 `EXPRESSION`，或明确拒绝 `PLUGIN`、`SCRIPT`、`EXTERNAL_SERVICE`。

gRPC 协议建议补充：

- `limit_mode`
- `limit_type`
- `limit_algorithm`
- `traffic_protocol`
- `quota_unit`
- `cost`
- `request_context`
- `rule_checksum`
- `snapshot_version`

建议测试：

- 启动全量加载后内存快照完整。
- 增量更新不会丢失未变更规则。
- 修改 `keyExtractor` 会触发 checksum 变化。
- `LOCAL_ONLY` 规则不会进入 StellPulsar。
- `GLOBAL_SYNC` 和 `GLOBAL_QUOTA` 能进入 StellPulsar。
- `CONCURRENCY` 和 `CONNECTION` 必须 release，否则并发额度会泄漏。

## stellpulsar-java-sdk

`stellpulsar-java-sdk` 是 Java 应用调用 StellPulsar 的客户端。它不应该直接读取 StellOrbit 管理接口，而是通过 `stellorbit-java-sdk` 获取治理规则。

需要适配：

- 从 `stellorbit-java-sdk` 获取 distributed rules 时，过滤条件改为：
  - `ruleType = RATE_LIMIT`
  - `coordinationMode in (GLOBAL_SYNC, GLOBAL_QUOTA)`
- `DistributedRateLimitRule` 模型补齐：
  - `limitMode`
  - `limitType`
  - `limitAlgorithm`
  - `trafficProtocol`
  - `requestMatcher`
  - `keyExtractor`
  - `quotaConfig`
  - `windowConfig`
  - `concurrencyConfig`
  - `hotspotConfig`
  - `customPolicy`
  - `modelLimitConfig`
  - `fallbackPolicy`
  - `responsePolicy`
  - `observabilityConfig`
  - `shadowConfig`
- `RateLimitRequest` 需要支持：
  - headers
  - gRPC metadata
  - remote IP
  - method/path
  - tenant/user/caller/apiKey
  - arbitrary attributes
  - cost
  - unit
- `tryAcquire` 内部先按 `requestMatcher` 匹配，再按 `keyExtractor` 生成 quota key。
- 对 `CONCURRENCY` 和 `CONNECTION` 增加生命周期 API：
  - `acquire(...)`
  - `release(...)`
  - 或返回 `Lease` / `Permit` 对象，由调用方 close/release。
- `FailPolicy` 应从规则级 `fallbackPolicy` 映射，而不是只使用全局配置。
- 对暂不支持的模式返回明确 reason：
  - `UNSUPPORTED_LIMIT_MODE`
  - `UNSUPPORTED_LIMIT_ALGORITHM`
  - `UNSUPPORTED_KEY_EXTRACTOR_SOURCE`

建议测试：

- 从 StellOrbit provider 中只筛出分布式规则。
- HTTP Header、gRPC Metadata 可以生成稳定 quota key。
- 并发类规则 acquire 后可以 release。
- 不支持的模式按 `fallbackPolicy` 处理。
- rule checksum 不一致时触发重新同步或返回规则冲突结果。

## stellpulsar-go-sdk

`stellpulsar-go-sdk` 是 Go 应用、网关和中间件调用 StellPulsar 的客户端。

需要适配：

- `rule.DistributedRateLimitRule` 补齐企业级字段。
- `orbit` bridge 从 `stellorbit-go-sdk` 新模型转换为 StellPulsar runtime view。
- `RateLimitRequest` 扩展：
  - `Headers map[string]string`
  - `Metadata map[string]string`
  - `Attributes map[string]string`
  - `RemoteIP string`
  - `Method string`
  - `Path string`
  - `Cost int64`
  - `Unit string`
- `rule` 包新增 key resolver：
  - `HEADER`
  - `GRPC_METADATA`
  - `HTTP_PATH`
  - `GRPC_METHOD`
  - `REMOTE_IP`
  - `TENANT`
  - `USER`
  - `CALLER`
  - `CUSTOM_EXPRESSION`
- `CUSTOM_EXPRESSION` 初期可以返回 unsupported，但必须有明确错误或 fallback。
- 增加并发类 API：
  - `Acquire(ctx, req) (*Permit, error)`
  - `Permit.Release(ctx)`
- `TryAcquire` 保留给 QPS、QUOTA、BANDWIDTH、MODEL token 类场景。

建议测试：

- `orbit` bridge 能转换所有新增字段。
- distributed rule filter 只接受 `GLOBAL_SYNC` 和 `GLOBAL_QUOTA`。
- Header、Metadata、Path、Tenant 等 key resolver 输出稳定 key。
- `Permit.Release` 幂等。
- 未实现模式按规则级 fallback 执行。

## 适配顺序

推荐按下面顺序推进：

1. 先升级 `stellorbit-java-sdk` 和 `stellorbit-go-sdk` 的规则模型，保证它们能完整解析和暴露新字段。
2. 升级 `stellpulsar-service` 的规则同步、内存快照、checksum 和增量更新。
3. 升级 `stellpulsar-java-sdk` 和 `stellpulsar-go-sdk` 的 Orbit bridge、key extractor 和 request model。
4. 增加 QPS、QUOTA、HEADER、BANDWIDTH、MODEL 的基础执行能力。
5. 增加 CONCURRENCY、CONNECTION 的 acquire/release 生命周期。
6. 增加 HOT_KEY 的热点统计和 TopN 状态。
7. 最后再开放 CUSTOM 的插件、脚本或外部服务模式。

## 最小兼容基线

下游第一阶段至少需要满足：

- 能解析所有新增字段。
- 能保留或忽略未知字段，但不能因为未知字段导致整体规则加载失败。
- 能按 `coordinationMode` 区分本地规则和分布式规则。
- 能按 `keyExtractor` 生成 quota key。
- 能把 `fallbackPolicy` 映射为 fail-open 或 fail-closed。
- 对未实现的 `limitMode` 给出明确 unsupported，而不是错误执行成 QPS。
- checksum、revision、snapshotVersion 必须覆盖新增字段。
- 全量同步只在启动或水位不可追赶时发生，其余场景应走增量。

## 发布与兼容建议

建议下游仓库使用同一批版本升级：

- `stellorbit-service` 发布企业级限流模型。
- `stellorbit-java-sdk` / `stellorbit-go-sdk` 发布支持新模型的 rule provider。
- `stellpulsar-service` 发布支持新 snapshot/delta 契约的服务端。
- `stellpulsar-java-sdk` / `stellpulsar-go-sdk` 发布支持新规则模型和 key extractor 的客户端。

版本策略建议：

- 如果 SDK 已有稳定用户，新增字段解析保持向后兼容。
- 如果当前仍是早期版本，可以直接升级模型，不保留旧结构别名。
- 下游运行时应优先识别 `coordinationMode`，仅在缺失时兼容读取 `enforcementMode`。

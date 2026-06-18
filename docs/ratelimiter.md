# 限流规则建模与发布边界

## 定位

Stellorbit Service 只负责限流规则的控制面建模、校验、版本化和发布，不承载分布式限流服务端的实时计算、配额分配、租约续期、热点分片、用量上报或重平衡。

限流运行时能力应由独立限流服务端、客户端治理引擎或网关组件承载。Stellorbit Service 发布的限流规则可以作为这些运行时组件的配置输入。

## 规则模型

限流规则由公共主表 `governance_rules` 和专属表 `rate_limit_rules` 共同表达。

`governance_rules` 保存规则通用生命周期：

- `rule_code`
- `rule_name`
- `rule_type = RATE_LIMIT`
- `source_format = CUE`
- `runtime_format = JSON`
- `cue_source`
- `priority`
- `enabled`
- `status`
- `draft_version`
- `latest_release_id`

`rate_limit_rules` 保存限流语义字段：

- `limit_type`：限流对象，例如请求、连接、租户、用户、API Key、模型 token 或成本。
- `limit_algorithm`：运行时算法声明，例如 token bucket、leaky bucket、fixed window、sliding window 或 quota lease。
- `execution_location`：执行位置声明，例如应用进程、sidecar、网关或边缘代理。
- `coordination_mode`：全局协调方式声明，例如本地独立、每请求全局同步或全局配额租约。
- `enforcement_mode`：旧版兼容字段，由 `execution_location + coordination_mode` 派生，不再作为事实源。
- `target_selector`：目标服务、接口、路由、命名空间或资源选择器。
- `dimensions`：限流维度，例如租户、用户、调用方、IP、Header 或业务标签。
- `quota_config`：配额大小、单位和业务上限。
- `window_config`：窗口长度或刷新周期。
- `burst_config`：突发容量配置。
- `model_limit_config`：大模型 token、成本、并发会话等配置。
- `fallback_policy`：运行时故障降级策略。
- `response_policy`：超限响应策略。

## 发布语义

发布时，Stellorbit Service 会将限流规则和其它治理规则一起编译为稳定运行时快照，并写入 `stellnula-service`。

快照中的限流规则只表达“应该如何限流”，不表达“由 Stellorbit Service 哪个节点执行限流”。独立限流运行时服务可以订阅相同配置中心内容，并基于自身的节点发现、配额算法和状态存储实现实时判定。

`EDGE` 只表示规则在入口网关、Ingress、边缘代理或 CDN POP 一类位置执行；是否需要独立分布式限流服务端参与，取决于 `coordination_mode` 是否为 `GLOBAL_SYNC` 或 `GLOBAL_QUOTA`。

## 分布式限流服务端同步接口

分布式限流服务端启动时通过 `GET /api/stellorbit/runtime/distributed-rate-limits/snapshot` 分页读取全量快照，完成本地规则表初始化。启动之后不应反复拉取全量快照，而是携带本地持有的 `snapshotVersion` 和 `checksum` 调用 `GET /api/stellorbit/runtime/distributed-rate-limits/changes` 拉取增量，或调用 `GET /api/stellorbit/runtime/distributed-rate-limits/watch` 建立 SSE 监听。

增量以配置中心下发配置单元为粒度，而不是单条规则为粒度。限流规则新增或修改时返回 `UPSERT_CONFIG`，其中 `config` 字段仍然是与配置中心下发一致的聚合内容；某个应用不再包含分布式限流规则时返回 `DELETE_CONFIG`，调用方应按 `configId` 删除本地配置。若调用方水位过旧、版本超前或校验和不一致，服务端会返回或推送 `FULL_SYNC_REQUIRED`，调用方需要重新分页全量同步。

## 不再承载的能力

当前项目不再包含以下能力：

- 运行时限流判定 API。
- 限流节点目录与固定 hash 分发。
- 配额策略、配额租约和续租。
- 客户端限流用量上报。
- 服务端本地 bucket 计数。
- 限流判定日志采样落库。
- 分布式限流重平衡和热点 key 分片。

这些能力属于独立分布式限流服务端的职责边界。

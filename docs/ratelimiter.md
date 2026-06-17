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
- `enforcement_mode`：执行模式声明，例如 local、global sync、global quota 或 edge。
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

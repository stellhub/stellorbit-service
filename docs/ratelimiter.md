# 企业级限流规则建模与发布边界

## 定位

Stellorbit Service 负责企业级限流规则的控制面建模、校验、版本化、发布和运行时同步，不承担请求路径上的实时限流判定。实时计数、分布式配额、租约续期、热点 key 探测、用量上报、重平衡和实际阻断动作由 Stellorbit Client、网关组件、边缘代理或独立分布式限流服务端执行。

限流规则发布后的内容必须满足两个约束：

- 对控制面友好：字段结构稳定，能够支持表单回显、校验、审计和差异对比。
- 对运行时友好：内容可以直接被本地限流器、网关限流器或分布式限流服务端消费，不依赖数据库结构。

## 总体模型

限流规则由公共主表 `governance_rules` 和专属表 `rate_limit_rules` 共同表达。

`governance_rules` 保存通用生命周期：

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

`rate_limit_rules` 保存限流语义。企业级模型分为九组字段：

| 字段组 | 字段 | 说明 |
| --- | --- | --- |
| 模式 | `limit_mode` | 一等限流模式，例如 QPS、并发、Header、热点、自定义、模型限流。 |
| 对象 | `limit_type` | 限流对象，例如请求、连接、字节、租户、用户、调用方、API Key、资源、模型 token、模型成本。 |
| 算法 | `limit_algorithm` | 运行时算法，例如令牌桶、漏桶、固定窗口、滑动窗口、并发计数、热点 key、配额租约、自定义算法。 |
| 协议 | `traffic_protocol` | 流量协议，例如 HTTP、gRPC、TCP、消息、模型调用或任意协议。 |
| 执行位置 | `execution_location` | 规则在哪里执行，例如应用进程、sidecar、网关、边缘代理。 |
| 协调方式 | `coordination_mode` | 是否需要全局协调，例如本地独立、每请求全局同步、全局配额租约。 |
| 匹配与取 key | `target_selector`、`request_matcher`、`key_extractor`、`dimensions` | 描述命中哪些流量，以及如何生成限流 key。 |
| 配额与算法参数 | `quota_config`、`window_config`、`burst_config`、`concurrency_config`、`hotspot_config`、`custom_policy`、`model_limit_config` | 描述限流阈值、窗口、突发、并发、热点、自定义策略和模型限流参数。 |
| 运行时策略 | `fallback_policy`、`response_policy`、`observability_config`、`shadow_config` | 描述失败兜底、超限响应、指标标签、影子模式和采样策略。 |

`enforcement_mode` 是旧版兼容字段，由 `execution_location + coordination_mode` 派生，不再作为事实源。

## 限流模式

`limit_mode` 是控制面和运行时识别限流场景的首要字段。

| `limit_mode` | 适用场景 | 推荐算法 | 典型 key |
| --- | --- | --- | --- |
| `QPS` | 接口 QPS、服务 QPS、租户 QPS、用户 QPS | `TOKEN_BUCKET`、`FIXED_WINDOW`、`SLIDING_WINDOW` | 应用、接口、租户、用户、调用方 |
| `CONCURRENCY` | HTTP/gRPC 并发请求数、连接并发、资源并发 | `CONCURRENCY_LIMIT` | 服务、接口、租户、用户、资源 |
| `HEADER` | HTTP Header 或 gRPC Metadata 维度限流 | `TOKEN_BUCKET`、`SLIDING_WINDOW` | Header、Metadata、租户、用户、调用方 |
| `HOT_KEY` | 热点租户、热点商品、热点资源、热点 API Key | `HOT_KEY`、`TOKEN_BUCKET` | 业务 key、资源 ID、API Key |
| `CUSTOM` | 自定义表达式、插件、脚本或外部策略 | `CUSTOM` | 自定义 key 表达式 |
| `QUOTA` | 分布式配额、租约、预分配额度 | `QUOTA_LEASE` | 租户、应用、节点、分片 |
| `BANDWIDTH` | 字节流量、下载、上传、出口带宽 | `TOKEN_BUCKET`、`LEAKY_BUCKET` | 服务、接口、用户、IP |
| `CONNECTION` | 长连接、TCP/gRPC 连接数 | `CONCURRENCY_LIMIT` | 服务、客户端、IP |
| `MODEL` | 大模型请求、token、成本、上下文、并发会话 | `TOKEN_BUCKET`、`SLIDING_WINDOW`、`CONCURRENCY_LIMIT` | 模型、租户、用户、调用方 |

## 协议与请求匹配

`traffic_protocol` 用于描述规则适用的协议层：

- `HTTP`：HTTP/1.1、HTTP/2 REST 或 Web API。
- `GRPC`：gRPC unary、server streaming、client streaming 或 bidi streaming。
- `TCP`：TCP 连接或字节流。
- `MESSAGE`：消息消费、Topic、Consumer Group。
- `MODEL`：大模型 API 调用。
- `ANY`：协议无关的业务维度限流。

`request_matcher` 用于表达协议级匹配条件：

- HTTP：method、path、pathPattern、query、headers、cookies、statusClass。
- gRPC：service、method、fullMethod、metadata、authority、peer。
- TCP：listener、remoteIp、remotePort、sni、alpn。
- MESSAGE：topic、consumerGroup、partition、messageHeaders。
- MODEL：provider、model、operation、streaming。

## 限流 key 提取

`key_extractor` 是企业级限流的核心字段，负责把请求上下文转换成稳定的限流 key。

推荐结构：

```json
{
  "strategy": "COMPOSITE",
  "failOnMissing": false,
  "hash": {
    "enabled": true,
    "algorithm": "MURMUR3_128"
  },
  "keys": [
    {
      "name": "tenant",
      "source": "HEADER",
      "key": "X-Tenant-Id",
      "required": true,
      "normalize": "LOWERCASE"
    },
    {
      "name": "api",
      "source": "HTTP_PATH",
      "key": "pathTemplate"
    }
  ]
}
```

支持的 `source` 至少包括：

- `HEADER`：HTTP Header。
- `GRPC_METADATA`：gRPC Metadata。
- `QUERY`：HTTP Query。
- `COOKIE`：HTTP Cookie。
- `PATH`：HTTP Path 变量。
- `HTTP_METHOD`：HTTP Method。
- `HTTP_PATH`：HTTP Path 或 Path Template。
- `GRPC_SERVICE`：gRPC Service。
- `GRPC_METHOD`：gRPC Method。
- `REMOTE_IP`：客户端 IP。
- `JWT_CLAIM`：JWT Claim。
- `API_KEY`：API Key。
- `TENANT`、`USER`、`CALLER`：治理上下文。
- `BODY_JSON_PATH`：请求体 JSONPath，通常只建议在应用侧执行。
- `CUSTOM_EXPRESSION`：自定义表达式。

`dimensions` 是 key 提取结果的声明式索引，运行时可以用它做指标标签、热点统计和调试展示。复杂维度应使用对象结构，而不是字符串数组。

## 配额与窗口

`quota_config` 表达配额本身：

- `limit`：上限值。
- `unit`：单位，例如 `REQUEST`、`CONNECTION`、`BYTE`、`TOKEN`、`COST`。
- `period`：业务周期，例如 `SECOND`、`MINUTE`、`HOUR`、`DAY`。
- `scope`：配额作用域，例如 `GLOBAL`、`PER_KEY`、`PER_INSTANCE`、`PER_TENANT`。
- `warmup`：预热限流参数。

`window_config` 表达统计窗口：

- `windowType`：`FIXED`、`SLIDING`、`ROLLING`。
- `durationMillis`：窗口长度。
- `bucketCount`：滑动窗口桶数。
- `precisionMillis`：统计精度。

`burst_config` 表达突发能力：

- `capacity`：桶容量或突发上限。
- `refillRate`：令牌补充速率。
- `maxBurstRatio`：最大突发比例。

## 并发限流

`limit_mode = CONCURRENCY` 使用 `concurrency_config` 表达：

- `maxConcurrent`：最大并发。
- `queueLimit`：等待队列上限。
- `queueTimeoutMillis`：排队超时。
- `adaptive`：是否启用自适应并发控制。
- `gradient`、`aimd`：自适应算法参数。
- `releaseOn`：并发释放时机，例如响应完成、流关闭、连接关闭。

HTTP 请求并发、gRPC unary 并发、gRPC streaming 并发、连接并发和资源并发都应该通过该模型表达。

## Header 与 gRPC Metadata 限流

`limit_mode = HEADER` 用于协议头维度限流。HTTP Header 和 gRPC Metadata 都通过 `key_extractor.keys` 表达：

```json
{
  "limitMode": "HEADER",
  "trafficProtocol": "GRPC",
  "keyExtractor": {
    "strategy": "COMPOSITE",
    "keys": [
      {
        "name": "tenant",
        "source": "GRPC_METADATA",
        "key": "x-tenant-id",
        "required": true
      },
      {
        "name": "method",
        "source": "GRPC_METHOD",
        "key": "method"
      }
    ]
  }
}
```

HTTP Header 限流同理使用 `source = HEADER`。Header 名称应统一大小写规范；gRPC Metadata 的 key 应按小写传递。

## 热点限流

`limit_mode = HOT_KEY` 使用 `hotspot_config` 表达热点识别和热点保护：

- `enabled`：是否启用热点保护。
- `metric`：热点统计指标，例如 QPS、并发、错误率、成本、token。
- `topN`：保留热点 key 数量。
- `threshold`：热点判定阈值。
- `ttlMillis`：热点 key 过期时间。
- `adaptive`：是否动态调整热点阈值。
- `isolation`：热点 key 是否独立桶、独立配额或独立降级。
- `degradePolicy`：热点降级策略。
- `sharding`：热点 key 分片策略。

热点探测、TopN 维护和动态阈值计算由运行时组件执行；Stellorbit Service 只发布策略。

## 自定义限流

`limit_mode = CUSTOM` 使用 `custom_policy` 表达：

- `policyType`：`EXPRESSION`、`SCRIPT`、`PLUGIN`、`EXTERNAL_SERVICE`。
- `language`：表达式或脚本语言，例如 CEL、SpEL、JS。
- `expression`：自定义 key 或判定表达式。
- `pluginName`、`pluginVersion`：插件标识。
- `endpoint`：外部策略服务地址。
- `timeoutMillis`：自定义策略超时。
- `failPolicy`：自定义策略失败后的处理方式。
- `parameters`：自定义参数。

自定义限流必须有明确的超时、失败策略和可观测性标签，避免自定义策略成为请求链路上的不可控依赖。

## 分布式协调

`coordination_mode` 表达运行时是否需要跨实例协调：

- `LOCAL_ONLY`：每个执行点独立计数，不依赖分布式限流服务端。
- `GLOBAL_SYNC`：每次判定需要同步访问分布式限流服务端，适合强一致全局限流。
- `GLOBAL_QUOTA`：运行时从分布式限流服务端租约式获取配额，本地消耗后续租。

只有 `GLOBAL_SYNC` 和 `GLOBAL_QUOTA` 会被分布式限流服务端同步接口选中。`EDGE` 只表示规则在入口网关、Ingress、边缘代理或 CDN POP 执行，不代表一定需要分布式协调。

## 运行时策略

`fallback_policy` 描述限流系统异常时如何处理：

- `FAIL_OPEN`
- `FAIL_CLOSED`
- `LOCAL_FALLBACK`
- `LAST_KNOWN_GOOD`

`response_policy` 描述超限响应：

- HTTP status，例如 429。
- gRPC status，例如 `RESOURCE_EXHAUSTED`。
- 错误码和错误消息。
- Retry-After。
- 响应 Header 或 Trailer。

`observability_config` 描述指标、日志和追踪：

- 指标标签白名单。
- 是否记录拒绝日志。
- 是否采样通过请求。
- 是否输出 top key。

`shadow_config` 描述影子模式：

- 只计算不阻断。
- 输出 wouldReject 标记。
- 仅对指定租户、用户或应用启用。

## 发布与生效

发布时，Stellorbit Service 会把结构化限流规则编译为稳定 JSON，并通过发布流程写入 `stellnula-service`。快照中的限流规则只表达“应该如何限流”，不表达“由 Stellorbit Service 自己执行限流”。

普通客户端、网关或边缘代理通过配置中心或运行时规则接口获取规则；分布式限流服务端通过专用同步接口获取所有分布式限流规则。

分布式限流服务端启动时通过 `GET /api/stellorbit/runtime/distributed-rate-limits/snapshot` 分页读取全量快照，完成本地规则表初始化。启动之后应携带本地 `snapshotVersion` 和 `checksum` 调用 `GET /api/stellorbit/runtime/distributed-rate-limits/changes` 拉取增量，或调用 `GET /api/stellorbit/runtime/distributed-rate-limits/watch` 建立 SSE 监听。

增量以配置中心下发配置单元为粒度。限流规则新增或修改时返回 `UPSERT_CONFIG`；某个应用不再包含分布式限流规则时返回 `DELETE_CONFIG`；调用方水位过旧、版本超前或校验和不一致时返回或推送 `FULL_SYNC_REQUIRED`。

## 职责边界

当前项目不承载以下运行时能力：

- 请求路径上的限流判定 API。
- 运行时 bucket、本地计数器或分布式计数器。
- 配额租约续期和配额再平衡。
- 热点 key TopN 维护和动态阈值计算。
- 客户端用量上报的聚合计算。
- 运行时节点目录、固定 hash 分发和热点 key 分片执行。

这些能力属于 Stellorbit Client、网关、边缘代理或独立分布式限流服务端。Stellorbit Service 提供专业、可验证、可发布、可同步的企业级限流规则契约。

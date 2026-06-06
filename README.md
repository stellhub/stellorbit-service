# Stellorbit Service

Stellorbit Service 是 StellHub 服务治理平台的数据面服务，中文名为“星轨”。它面向微服务运行时提供路由、熔断、鉴权、限流、实例空间等治理规则的统一承载、发布与运行时协同能力，并作为分布式限流服务端承接客户端的动态限流请求。

本项目对标 Istio 的治理理念进行设计：规则在服务治理控制面创建、修改、校验和发布，最终由 Stellorbit Service 生成数据面快照，并通过 `stellnula-service` 配置中心下发给客户端。运行时流量治理尽量下沉到客户端本地执行；必须全局协同的能力，例如分布式限流配额协调，则由 Stellorbit Service 提供服务端仲裁能力。

## 问题分析

Stellorbit Service 需要同时支撑服务治理控制面、前端控制台、配置中心发布链路和客户端运行时。它不是单纯的规则存储服务，而是规则数据面、发布编排器和分布式限流服务端。

结合 [auth.md](docs/auth.md)、[router.md](docs/router.md)、[breaker.md](docs/breaker.md) 和 [ratelimiter.md](docs/ratelimiter.md)，当前只使用一个 `governance_rules` 表保存全部规则并不足够。单表可以作为公共规则主表，用于保存规则基础信息、生命周期状态、CUE 原文、运行时快照和发布关系；但鉴权、路由、熔断、限流的结构化字段差异很大，如果全部塞进一份 JSON，会带来以下问题：

- 控制面 CRUD 难以做类型化校验和前端表单回显。
- 路由规则无法高效按路由类型、协议、流量方向、目标服务和匹配条件查询。
- 熔断规则无法清晰表达连接池、连续错误、错误率、慢调用、异常分类、半开探测、重试预算等多类配置。
- 限流规则无法区分本地限流、全局同步限流、异步配额限流和大模型 token/cost 限流。
- 鉴权规则无法按 Istio 的 PeerAuthentication、RequestAuthentication、AuthorizationPolicy 模型保存，也缺少 mTLS 证书、JWT/JWKS 和证书下发记录。
- 分布式限流服务端无法保存用户选择的配额分配算法、配额租约、用量上报和重平衡状态。

因此数据库采用“公共主表 + 规则专属表 + 发布快照 + 下发记录 + 运行时状态”的模型：

```text
governance_rules
  ├── route_rules
  ├── breaker_rules
  ├── rate_limit_rules
  └── auth_policy_rules
```

公共主表负责规则生命周期，专属表负责规则语义字段。发布时按 `governance_rules` 聚合规则，再读取专属表生成客户端快照。

## 设计

### 总体定位

| 组件 | 职责 |
| --- | --- |
| 服务治理控制面 | 提供控制面 API，供前端执行实例空间、规则、证书、限流算法、发布和回滚的 CRUD |
| `stellorbit-service` | 服务治理数据面，负责规则保存、校验、发布编排、客户端快照生成、分布式限流服务端判定 |
| `stellnula-service` | 配置中心，负责发布后规则快照、mTLS 证书、JWKS、限流配置的存储、版本管理、监听与下发 |
| Stellorbit Client | 客户端治理引擎，接收规则快照并执行路由、熔断、鉴权、本地限流，必要时请求服务端限流判定 |

### CUE 规则格式

Stellorbit Service 使用 CUE 作为管理面规则源格式。平台保存用户提交的 CUE 原文，并在发布时完成以下处理：

1. Schema 校验：校验规则类型、作用域、目标服务、条件表达式和策略参数。
2. 默认值合并：补齐超时、兜底策略、限流窗口、熔断恢复策略、mTLS 模式等默认配置。
3. 冲突检查：检查同一作用域内的路由优先级、限流维度、鉴权资源、证书绑定和熔断目标是否冲突。
4. 快照编译：将 CUE 编译为客户端稳定消费的 JSON 或 Protobuf 快照。
5. 内容校验：生成 checksum，保证发布内容与客户端接收内容可追溯。

运行时推荐格式：

```text
CUE 规则源文件 -> 发布前校验和默认值合并 -> JSON/Protobuf 规则快照 -> stellnula-service -> Stellorbit Client
```

`stellnula-service` 只负责配置存储、版本管理和下发，不承担 Stellorbit 规则语义校验；规则语义校验必须在 Stellorbit Service 发布阶段完成。

服务端发布前会调用 CUE CLI 执行真实编译，运行环境需要安装 `cue` 命令，或通过 `STELLORBIT_CUE_BINARY` 指定可执行文件路径。编译链路固定执行以下步骤：

1. 读取当前规则类型对应的 CUE Schema，优先使用 `cue_schema_versions` 中处于 `ACTIVE` 状态的版本，未配置时使用内置 `stellorbit.governance.v1`。
2. 将结构化专属表生成的基础模型、用户提交的 `cueSource` 和 CUE Schema 进行统一合并。
3. 通过 `cue export` 物化默认值并导出规范化 JSON。
4. 使用同一份规范化 JSON 计算 checksum，并转换为 Protobuf `Struct` 二进制快照。
5. 执行跨规则冲突检测和与上一发布版本的兼容性校验。
6. dry-run 接口返回每条规则的 schemaVersion、checksum、JSON、Protobuf Base64、warning 和 explain，不创建发布记录。

### 规则生命周期

规则采用显式发布模型：

1. 创建规则：控制面写入 `governance_rules` 和对应规则专属表，状态为 `DRAFT`。
2. 校验规则：执行 CUE Schema、跨规则冲突、证书有效期、目标服务存在性和配额算法参数校验。
3. 发布规则：生成发布版本、发布项、完整运行时快照和异步发布任务。
4. 下发规则：发布 worker 从 `publish_jobs` 拉取任务，将规则快照、mTLS 证书、JWKS 或限流配置写入 `stellnula-service`。
5. 客户端生效：客户端监听配置变更，完成本地热更新。
6. 回滚规则：按历史发布版本重新生成快照并再次下发。

发布链路采用 outbox/job 模型，不在 HTTP 请求线程中直接调用外部配置中心：

1. 控制面提交发布后，Stellorbit 创建 `rule_releases`、`release_items`、`stellnula_publish_records` 和 `publish_jobs`，并返回 `PUBLISHING` 状态。
2. `publish_locks` 按 `instanceSpaceId + applicationId` 加应用级发布锁，防止同一应用并发发布多个版本。
3. 发布 worker 定时扫描 `PENDING` 或到期的 `FAILED` job，按幂等键重放单条发布记录。
4. 每条发布记录独立退避重试，成功后写回 Stellnula 的 `releaseNo`、`version`、`revision`、`checksum`。
5. 卡住的 `RUNNING` job 会被重新入队；卡住的 `PUBLISHING` release 会触发反查和补偿任务。
6. worker 会通过 Stellnula 反查接口 reconciliation 已经成功但本地未落状态的记录。
7. release 最终汇总所有 publish record 状态，进入 `PUBLISHED`、`PARTIAL_PUBLISHED` 或 `FAILED`，并释放发布锁。

### 鉴权规则

鉴权规则对标 Istio 安全模型，分为三类核心对象：

- PeerAuthentication：链路级认证，配置 mTLS 模式，例如 `STRICT`、`PERMISSIVE`、`DISABLE`。
- RequestAuthentication：请求级认证，配置 JWT issuer、audience、JWKS、claim 映射和 token 提取位置。
- AuthorizationPolicy：请求级授权，配置 `CUSTOM`、`DENY`、`ALLOW`、`AUDIT` 动作，以及 `from`、`to`、`when` 条件。

鉴权规则需要保存 mTLS 证书，并支持通过配置中心下发给客户端。数据库中由 `mtls_certificates` 保存证书链、公钥证书、加密后的私钥、指纹、有效期、证书用途和证书状态；由 `auth_rule_certificates` 关联鉴权规则与证书；发布时由 `stellnula_publish_records` 记录证书或 JWKS 写入 `stellnula-service` 的结果。

### 路由规则

路由规则对标 Istio VirtualService、DestinationRule、Gateway 和 Envoy 路由能力，至少支持以下类型：

- HTTP、gRPC、TCP、TLS/SNI 路由。
- 权重分流、灰度路由、Header/Query/Cookie 匹配、接口级路由。
- 可用区优先、地域优先、故障转移、服务版本路由。
- 请求镜像、重写、重定向、直接响应、故障注入。
- 超时、重试、负载均衡策略和出口流量治理。

数据库中 `route_rules` 保存路由类型、协议、流量方向、主机、匹配条件、目标集合、路由动作、负载均衡策略、超时重试和故障注入配置，使控制面可以按路由类型做 CRUD、查询、校验和发布。

### 熔断规则

熔断规则对标 Istio DestinationRule、Envoy Circuit Breaking、Outlier Detection 和 Resilience4j 模型，至少支持以下类型：

- 资源限额：最大连接数、最大挂起请求数、最大并发请求数、最大重试数。
- 连续错误：按连续 5xx、连接失败、超时触发。
- 错误比例：按滑动窗口失败率触发。
- 慢调用比例：按慢调用阈值和慢调用比例触发。
- 异常分类：配置记录异常和忽略异常。
- 实例摘除：按异常检测临时摘除异常实例。
- 半开探测：Open 后允许有限请求探测恢复。
- 重试预算和并发隔离：防止重试放大和资源耗尽。

数据库中 `breaker_rules` 保存熔断类型、目标选择器、滑动窗口、阈值、连接池、异常实例摘除、半开探测、异常分类、重试预算和降级策略，支持控制面自定义多种熔断规则。

### 限流规则

限流规则覆盖本地限流、全局限流、异步配额限流和大模型应用限流。

普通限流能力包括：

- 固定窗口、滑动窗口、令牌桶、漏桶。
- 按应用、接口、租户、用户、调用方、IP、Header、API Key、实例空间组合维度限流。
- 本地限流、每请求全局同步限流、异步配额租约限流。
- fail-open、fail-closed、local-fallback、last-assignment 等故障降级策略。

大模型应用限流能力包括：

- 按模型供应商、模型名称、API、租户、用户和调用方限流。
- 按请求数、输入 token、输出 token、总 token、费用、并发会话、上下文窗口等指标限流。
- 支持 token 预估、实际用量上报、成本预算和超额降级策略。

数据库中 `rate_limit_rules` 保存限流类型、算法、维度、配额、窗口、突发量、大模型配额指标和兜底策略。

### 分布式限流配额算法

Stellorbit Service 作为分布式限流服务端，需要允许用户配置不同的限流配额分配算法。数据库中 `rate_limit_quota_policies` 保存配额协调策略，支持：

- `EQUAL_SPLIT`：按客户端实例均分配额。
- `WEIGHTED_SPLIT`：按实例权重或容量比例分配。
- `DEMAND_AWARE`：按周期上报用量动态重平衡。
- `HOTSPOT_AWARE`：对热点 key 做虚拟分片和倾斜修正。
- `BORROWING`：允许冷实例配额被热实例借用。
- `MANUAL`：由控制面指定固定配额分配。

运行时由 `rate_limit_quota_assignments` 保存服务端给客户端的配额租约，由 `rate_limit_usage_reports` 保存客户端周期上报用量，由 `rate_limit_buckets` 和 `rate_limit_decisions` 保存服务端同步判定状态和判定日志。

### 运行时限流最终方案

限流服务端是高 QPS 运行时服务，最终方案以性能和可用性优先，可以牺牲短时间窗口内的强一致性。Stellorbit Service 不把 Redis 放到客户端请求链路中，也不要求客户端访问 Redis。运行时限流采用“本地全量内存 + DB 秒级同步 + 客户端固定节点哈希”的方案：

1. 每个 Stellorbit 节点启动后从 DB 全量加载已发布、已启用的限流规则。
2. 节点按秒级定时从 DB 刷新规则、发布版本和配额配置，并维护规则缓存水位；DB 是规则事实源，不参与每次限流判定。
3. 限流计数只在 Stellorbit 节点本地内存中完成，避免高 QPS 请求路径访问 DB 或 Redis；`rate_limit_decisions` 只通过内存队列采样后异步批量落库。
4. 客户端首次连接 Stellorbit 时获取全部可用节点列表。
5. 客户端按 `rateLimitRuleId + limitKeyHash` 做固定哈希，直连固定 Stellorbit 节点执行限流判定；节点目录返回 `ringVersion`，客户端可据此判断 hash ring 是否变化。
6. 固定哈希避免同一个限流 key 在多个节点之间漂移，从而减少多节点本地计数带来的限流偏差。
7. 对热点 key 可按 `hotspotShardCount` 在节点本地继续分片，配额按分片数均摊，避免单 key 的本地锁竞争。
8. 节点窗口计数过期后由本地清理任务回收；用量上报、审计和历史分析走单独 API 或异步批量写入。
9. 大模型限流支持按 `REQUESTS`、`INPUT_TOKENS`、`OUTPUT_TOKENS`、`TOTAL_TOKENS`、`COST` 计算实际消耗 permits。
10. 配额租约通过 `/api/stellorbit/runtime/rate-limit/assignments/lease` 分配或复用，客户端通过 `/api/stellorbit/runtime/rate-limit/usage-reports` 周期上报用量。

该方案的取舍是：单个 key 在正常情况下会稳定落到同一个 Stellorbit 节点，获得接近单节点精确的限流效果；当节点上下线、客户端重连或节点列表变化时，可能发生短时间 key 漂移和配额偏差。对于服务治理场景，这种最终一致通常比引入额外 Redis 故障点更适合。

Redis 不作为默认依赖。后续如果出现必须强一致的全局限流场景，可以作为可选后端接入到 Stellorbit 服务端内部，但客户端请求仍然只访问 Stellorbit，不直接访问 Redis。

### 客户端规则拉取与监听协议

Stellorbit Client 与 Stellorbit Service 之间使用独立的运行时规则协议，不直接暴露控制面数据库结构。客户端启动后先完成协议协商，再拉取快照、监听变化、上报 ACK 和实际生效状态：

1. 版本协商：客户端调用 `/api/stellorbit/runtime/rules/negotiate`，上报 `clientVersion`、支持的协议版本和可接受的 `JSON/PROTOBUF` 快照格式。
2. 快照拉取：客户端调用 `/api/stellorbit/runtime/rules/snapshot`，按 `instanceSpaceId + applicationId + clientId` 获取当前可见发布版本。
3. 监听变化：客户端调用 `/api/stellorbit/runtime/rules/watch` 建立 SSE 长轮询，收到 `SNAPSHOT_CHANGED` 后重新应用快照。
4. ACK 回执：客户端应用或拒绝快照后调用 `/api/stellorbit/runtime/rules/acks`，写入 `client_runtime_acks`。
5. 生效上报：客户端周期调用 `/api/stellorbit/runtime/rules/status-reports`，上报每条规则是否生效、失败原因和回滚状态。
6. 心跳与实例视图：客户端调用 `/api/stellorbit/runtime/rules/heartbeats` 刷新会话，控制面可通过 `/api/stellorbit/runtime/rules/instances` 查询在线客户端、版本、水位和 hash ring 版本。

Runtime snapshot schema 首版固定为 `stellorbit.runtime.snapshot.v1`，协议版本固定为 `stellorbit.runtime.protocol.v1`。快照响应不直接返回 Entity，而是返回稳定运行时模型：

```text
RuntimeRuleSnapshot
  releaseId
  releaseVersion
  releaseStatus
  protocolVersion
  snapshotSchemaVersion
  runtimeFormat
  changed
  grayMatched
  compatibilityMode
  checksum
  publishedAt
  generatedAt
  snapshotJson | snapshotBytesBase64
  rules[]
  rateLimitNodeDirectory
```

JSON/Protobuf 兼容策略：

- JSON 是规范语义模型，字段名保持稳定，新增字段必须兼容旧客户端。
- Protobuf 是高性能二进制传输格式，删除字段必须保留 field number，枚举只允许追加。
- 客户端未知字段按 ignore 处理，服务端通过 `snapshotSchemaVersion` 和 `protocolVersion` 做兼容性判断。
- 当客户端请求的格式与发布快照格式不一致时，服务端优先返回已有可用格式，并通过 `compatibilityMode=JSON_CANONICAL_PROTOBUF_OPTIONAL` 告知兼容模式。
- 二进制快照在 HTTP JSON 响应中使用 Base64 表达；后续 gRPC 接口可直接返回 bytes。

规则灰度下发通过快照中的 `grayPolicy` 控制。首版支持按 `clientLabels` 精确匹配，也支持按 `clientId` 做百分比哈希。未命中灰度的客户端会继续保持当前版本，不会看到灰度快照。

客户端 hash 到限流节点的协议与规则快照一起返回 `rateLimitNodeDirectory`，字段包括 `hashStrategy`、`hashKeyHint`、`ringVersion` 和节点列表。客户端必须使用 `rateLimitRuleId + ':' + limitKeyHash` 作为 hash key，并在 `ringVersion` 变化时重新计算固定节点。

### 发布到配置中心

发布动作会生成稳定的运行时快照，并通过 `stellnula-service` 进行下发。建议配置组织方式：

```text
/stellorbit/{instanceSpace}/{application}/governance-rules.json
/stellorbit/{instanceSpace}/{application}/route-rules.json
/stellorbit/{instanceSpace}/{application}/breaker-rules.json
/stellorbit/{instanceSpace}/{application}/rate-limit-rules.json
/stellorbit/{instanceSpace}/{application}/auth-rules.json
/stellorbit/{instanceSpace}/{application}/mtls-certificates.json
/stellorbit/{instanceSpace}/{application}/jwks.json
```

配置内容应包含：

- `version`：发布版本。
- `instanceSpace`：实例空间。
- `application`：目标应用。
- `publishedAt`：发布时间。
- `sourceFormat`：规则源格式，固定为 `CUE`。
- `runtimeFormat`：运行时快照格式，可为 `JSON` 或 `PROTOBUF`。
- `rules`：规则列表。
- `certificates`：证书、证书指纹和证书引用。
- `checksum`：内容校验值。
- `rollbackFrom`：回滚来源版本，可为空。

## 实现

### 技术栈

当前工程使用 Spring Boot 构建，核心依赖包括：

- Spring Boot Web：提供 HTTP API。
- Spring Boot Validation：提供请求参数和规则模型校验。
- Spring Boot JDBC + PostgreSQL：承载规则、发布版本、证书、限流配额和审计数据。
- Spring Boot Actuator：提供健康检查和运行时观测入口。
- gRPC + Protobuf：预留高性能客户端通信和分布式限流运行时接口。
- Stellflux Starters：接入 HTTP、gRPC、缓存、日志、指标和链路追踪基础设施。
- CUE：作为治理规则 DSL、Schema 约束和发布前校验格式。

### 数据库设计

数据库首版设计写在 [schema.sql](schema.sql) 中，面向 PostgreSQL，核心结构包括：

- 公共规则主表：`governance_rules`。
- 规则专属表：`auth_policy_rules`、`route_rules`、`breaker_rules`、`rate_limit_rules`。
- 证书表：`mtls_certificates`、`auth_rule_certificates`。
- 配额算法表：`rate_limit_quota_policies`。
- 分布式限流运行时表：`rate_limit_quota_assignments`、`rate_limit_usage_reports`、`rate_limit_buckets`、`rate_limit_decisions`。
- 发布表：`rule_validations`、`rule_releases`、`release_items`、`stellnula_publish_records`。
- 发布作业表：`publish_locks`、`publish_jobs`。
- 审计表：`audit_events`。

### 控制面 API 草案

控制面 API 提供给服务治理控制面和前端使用，负责 CRUD、校验、发布和回滚。

控制面请求必须透传以下安全上下文，Stellorbit Service 会用它们完成 RBAC、租户隔离、数据权限、发布人/审批人身份记录和操作审计：

| Header | 说明 |
| --- | --- |
| `X-Stellorbit-Tenant-Id` | 当前租户 ID，实例空间和审计事件会按租户隔离 |
| `X-Stellorbit-Instance-Space-Id` | 当前实例空间 ID，除实例空间自身管理接口外必须传递 |
| `X-Stellorbit-Operator` | 当前操作人，服务端以该值写入创建人、更新人、发布人、审批人和审计人 |
| `X-Stellorbit-Roles` | 当前角色列表，逗号分隔，支持 `ADMIN`、`VIEWER`、`OPERATOR`、`PUBLISHER`、`APPROVER`、`SECURITY_ADMIN` |
| `X-Stellorbit-Reason` | 操作原因，所有 `POST`、`PUT`、`PATCH`、`DELETE` 控制面写操作必须传递 |
| `X-Request-Id` | 请求链路 ID，用于审计追踪 |

RBAC 首版约定：查询接口允许只读角色访问；规则、证书和基础资源写操作需要 `OPERATOR`；发布、重试、回滚和人工恢复需要 `PUBLISHER`；审批通过和驳回需要 `APPROVER`；证书等安全资源需要 `SECURITY_ADMIN` 或 `OPERATOR`；`ADMIN` 拥有所有控制面权限。发布审批会校验审批人不能和发布创建人或发布人相同。

控制面写操作会统一写入 `audit_events`，记录租户、实例空间、操作人、操作原因、请求路径、HTTP 状态、请求 ID、角色、来源 IP 和 User-Agent。运行时限流 API 不走这套控制面拦截和审计链路，避免高 QPS 请求被控制面权限模型影响。

```text
POST   /api/stellorbit/instance-spaces
GET    /api/stellorbit/instance-spaces
PATCH  /api/stellorbit/instance-spaces/{spaceId}

POST   /api/stellorbit/applications
GET    /api/stellorbit/applications
PATCH  /api/stellorbit/applications/{applicationId}

POST   /api/stellorbit/rules/routes
GET    /api/stellorbit/rules/routes
GET    /api/stellorbit/rules/routes/{ruleId}
PATCH  /api/stellorbit/rules/routes/{ruleId}
DELETE /api/stellorbit/rules/routes/{ruleId}

POST   /api/stellorbit/rules/breakers
GET    /api/stellorbit/rules/breakers
GET    /api/stellorbit/rules/breakers/{ruleId}
PATCH  /api/stellorbit/rules/breakers/{ruleId}
DELETE /api/stellorbit/rules/breakers/{ruleId}

POST   /api/stellorbit/rules/rate-limits
GET    /api/stellorbit/rules/rate-limits
GET    /api/stellorbit/rules/rate-limits/{ruleId}
PATCH  /api/stellorbit/rules/rate-limits/{ruleId}
DELETE /api/stellorbit/rules/rate-limits/{ruleId}

POST   /api/stellorbit/rules/auth
GET    /api/stellorbit/rules/auth
GET    /api/stellorbit/rules/auth/{ruleId}
PATCH  /api/stellorbit/rules/auth/{ruleId}
DELETE /api/stellorbit/rules/auth/{ruleId}

POST   /api/stellorbit/security/mtls-certificates
GET    /api/stellorbit/security/mtls-certificates
PATCH  /api/stellorbit/security/mtls-certificates/{certificateId}

POST   /api/stellorbit/rule-releases
POST   /api/stellorbit/rule-releases/dry-run
GET    /api/stellorbit/rule-releases
GET    /api/stellorbit/rule-releases/{releaseId}
GET    /api/stellorbit/rule-releases/{releaseId}/diff
GET    /api/stellorbit/rule-releases/{releaseId}/impact
POST   /api/stellorbit/rule-releases/{releaseId}/retry
POST   /api/stellorbit/rule-releases/{releaseId}/rollback
GET    /api/stellorbit/rule-releases/{releaseId}/approvals
POST   /api/stellorbit/rule-releases/{releaseId}/approvals/submit
POST   /api/stellorbit/rule-releases/{releaseId}/approvals/approve
POST   /api/stellorbit/rule-releases/{releaseId}/approvals/reject
```

运行时 API 提供给客户端：

```text
GET  /api/stellorbit/runtime/rate-limit/nodes
GET  /api/stellorbit/runtime/rate-limit/local-state
POST /api/stellorbit/runtime/rate-limit/evaluate
POST /api/stellorbit/runtime/rate-limit/usage-reports
POST /api/stellorbit/runtime/rate-limit/assignments/lease

POST /api/stellorbit/runtime/rules/negotiate
GET  /api/stellorbit/runtime/rules/snapshot
GET  /api/stellorbit/runtime/rules/watch
POST /api/stellorbit/runtime/rules/acks
POST /api/stellorbit/runtime/rules/status-reports
POST /api/stellorbit/runtime/rules/heartbeats
GET  /api/stellorbit/runtime/rules/instances
```

客户端启动顺序建议：

1. 调用 `POST /api/stellorbit/runtime/rules/negotiate` 协商协议、快照格式和 schema 版本。
2. 调用 `GET /api/stellorbit/runtime/rules/snapshot` 获取首个规则快照和 `rateLimitNodeDirectory`。
3. 使用返回的 `hashStrategy` 对 `rateLimitRuleId + ':' + limitKeyHash` 做固定哈希。
4. 将同一个限流 key 的请求固定发送到同一个 Stellorbit 节点。
5. 调用 `GET /api/stellorbit/runtime/rules/watch` 监听规则变化，应用后写 ACK 和生效状态。
6. 节点异常或 `ringVersion` 变化时重新拉取节点列表并重新计算路由。

### 本地构建

项目使用 Maven 构建：

```bash
mvn clean verify
```

### 运行服务

```bash
mvn spring-boot:run
```

服务启动后可通过 Actuator 检查健康状态：

```text
GET /actuator/health
```

## 完整代码

当前 README 定义 Stellorbit Service 的首版产品边界、规则格式、数据模型和控制面接口边界。后续代码实现应围绕以下完整闭环推进：

1. 规则草稿管理：按类型完成路由、熔断、限流、鉴权规则的 CRUD。
2. 证书管理：保存 mTLS 证书、证书指纹、加密私钥和证书下发记录。
3. 规则发布管理：完成 CUE 校验、版本生成、发布项生成、审计记录和回滚能力。
4. 配置中心集成：发布时将规则快照、mTLS 证书和 JWKS 写入 `stellnula-service`。
5. 分布式限流服务端：支持同步判定、异步配额分配、用量上报和大模型限流。
6. 可观测性：接入指标、日志、追踪、审计和健康检查。

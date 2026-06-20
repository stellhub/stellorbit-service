# Stellorbit Service

[English](README.md) | [简体中文](README_CN.md)

Stellorbit Service 是 StellHub 体系中的服务治理规则数据面服务，中文名为“星轨”。它负责保存类型化治理规则、校验 CUE 规则源、编译稳定运行时快照，并将发布后的配置写入 `stellnula-service`，供客户端和独立运行时组件一致消费。

本项目的职责边界非常明确：

- Stellorbit Service 负责规则建模、校验、发布编排、运行时快照生成、审计数据和控制面 API。
- `stellnula-service` 负责配置存储、版本管理、监听和下发。
- 独立运行时组件负责数据面实时决策，例如分布式限流、配额分配、租约续期、用量上报、热点分片和重平衡。

## 项目定位

服务治理规则不是简单的 key-value 配置。路由、熔断、限流和鉴权都需要类型化字段、生命周期、Schema 校验、冲突检测、发布历史和运行时下发语义。

因此 Stellorbit Service 采用“公共规则主表 + 规则专属表”的建模方式：

```text
governance_rules
  |-- route_rules
  |-- breaker_rules
  |-- rate_limit_rules
  `-- auth_policy_rules
```

`governance_rules` 保存规则生命周期字段，例如规则编码、规则名称、规则类型、源格式、运行时快照、状态、版本和发布关系。各规则专属表保存需要被查询、校验和前端回显的结构化语义字段。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 类型化治理模型 | 为路由、熔断、限流、鉴权分别建立专属规则模型。 |
| CUE 校验流水线 | 支持规则源校验、默认值合并、JSON 导出、checksum 生成、冲突检测和兼容性校验。 |
| 发布编排 | 支持发布版本、发布项、发布记录、异步发布任务、发布锁、重试、恢复和回滚。 |
| 配置中心发布 | 将治理规则快照、mTLS 证书、JWKS 和限流配置发布到 `stellnula-service`。 |
| 运行时下发协议 | 提供客户端快照拉取、监听、ACK、生效状态上报、心跳和实例视图接口。 |
| 分布式限流同步 | 支持独立分布式限流服务端启动全量同步和运行期增量同步。 |
| 安全与审计 | 支持控制面 Header、RBAC 约定、操作原因、发布审批和不可变审计事件。 |
| 可观测性 | 集成 Spring Boot Actuator、Stellflux HTTP、日志、指标、追踪和 Caffeine 遥测。 |

## 架构角色

| 组件 | 职责 |
| --- | --- |
| 服务治理控制面 | 面向用户和控制台，管理实例空间、应用、规则、发布、审批和回滚。 |
| Stellorbit Service | 规则数据面服务，负责持久化、校验、编译、发布和运行时快照暴露。 |
| `stellnula-service` | 配置中心，负责发布后配置的存储、版本管理、监听和下发。 |
| 独立限流运行时服务 | 承载分布式限流判定、配额协调、用量上报和重平衡。 |
| Stellorbit Client | 客户端治理引擎，消费快照并执行路由、熔断、鉴权和本地限流策略。 |

## 规则领域

### 路由规则

路由规则对标 Istio `VirtualService`、`DestinationRule`、`Gateway` 和 Envoy 路由能力，覆盖 HTTP、TCP、TLS/SNI 路由、权重分流、灰度路由、Header/Query/Cookie 匹配、请求镜像、重写、重定向、直接响应、故障注入、超时、重试、负载均衡和故障转移。

详见 [docs/router.md](docs/router.md)。

### 熔断规则

熔断规则覆盖连接池、资源限额、异常实例摘除、滑动窗口错误率、慢调用比例、半开探测、重试预算、降级策略和异常分类等能力。

详见 [docs/breaker.md](docs/breaker.md)。

### 限流规则

限流规则描述“应该如何限流”和“在哪里执行限流”。Stellorbit Service 不承担运行时限流判定。

当前模型将限流规则拆为：

- `execution_location`：`APPLICATION`、`SIDECAR`、`GATEWAY` 或 `EDGE`。
- `coordination_mode`：`LOCAL_ONLY`、`GLOBAL_SYNC` 或 `GLOBAL_QUOTA`。
- `enforcement_mode`：旧版兼容字段，由上述两个字段派生。

分布式限流规则会以与配置中心下发一致的格式同步给独立分布式限流服务端。

详见 [docs/ratelimiter.md](docs/ratelimiter.md)。

### 鉴权规则

鉴权规则对标 Istio 安全模型，覆盖：

- `PeerAuthentication`：链路级 mTLS 姿态。
- `RequestAuthentication`：JWT issuer、audience、JWKS、claim 映射和 token 提取。
- `AuthorizationPolicy`：`CUSTOM`、`DENY`、`ALLOW`、`AUDIT` 决策。

Stellorbit Service 同时建模 mTLS 证书、证书绑定、JWKS 发布、证书有效期和下发记录。

详见 [docs/auth.md](docs/auth.md)。

## CUE 编译流水线

Stellorbit Service 保存用户提交的 CUE 规则源，并在发布前编译为稳定 JSON 运行时快照：

1. 读取当前规则类型的激活 CUE Schema。
2. 合并结构化专属表数据、用户提交的 CUE 源和 Schema 默认值。
3. 调用 `cue export` 生成规范化 JSON。
4. 基于规范化内容生成稳定 checksum。
5. 执行跨规则冲突检测和兼容性校验。
6. dry-run 返回诊断信息，正式发布创建发布记录。

运行时格式固定为：

```text
CUE 源 -> Schema/默认值合并 -> JSON 快照 -> stellnula-service -> 运行时客户端
```

## 运行时下发协议

Stellorbit Client 使用独立运行时协议，不直接读取控制面数据库结构：

```text
POST /api/stellorbit/runtime/rules/negotiate
GET  /api/stellorbit/runtime/rules/snapshot
GET  /api/stellorbit/runtime/rules/watch
POST /api/stellorbit/runtime/rules/acks
POST /api/stellorbit/runtime/rules/status-reports
POST /api/stellorbit/runtime/rules/heartbeats
GET  /api/stellorbit/runtime/rules/instances
```

当前运行时快照 Schema 为 `stellorbit.runtime.snapshot.v1`，协议版本为 `stellorbit.runtime.protocol.v1`。

## 分布式限流同步

独立分布式限流服务端不应该在运行期反复拉取全量快照。推荐流程如下：

1. 启动时分页拉取完整分布式限流配置：

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/snapshot?page=0&size=100
   ```

2. 运行期携带本地水位拉取增量：

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/changes?currentSnapshotVersion=1&currentChecksum=...
   ```

3. 建立 SSE 监听以便及时接收增量：

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/watch?currentSnapshotVersion=1&currentChecksum=...
   ```

增量粒度为配置中心下发配置单元：

- `UPSERT_CONFIG`：按 `configId` 新增或替换本地配置。
- `DELETE_CONFIG`：按 `configId` 删除本地配置。
- `FULL_SYNC_REQUIRED`：当前水位无法通过本机增量日志追赶，需要重新分页全量同步。

本机快照由 Stellflux Caffeine 缓存承载，并保留有界增量日志用于运行时追赶。

## 发布模型

发布链路采用 outbox/job 模型，不在 HTTP 请求线程中直接调用配置中心：

1. 创建 `rule_releases`、`release_items`、`stellnula_publish_records` 和 `publish_jobs`。
2. 使用发布锁按应用维度串行化发布。
3. 发布 worker 使用幂等键重试失败任务。
4. reconciliation 从 `stellnula-service` 反查已经成功但本地未落状态的发布。
5. 最终状态进入 `PUBLISHED`、`PARTIAL_PUBLISHED` 或 `FAILED`。

## 控制面安全 Header

控制面写接口需要透传以下 Header，用于租户隔离、RBAC、发布责任归属和审计：

| Header | 说明 |
| --- | --- |
| `X-Stellorbit-Tenant-Id` | 当前租户 ID。 |
| `X-Stellorbit-Instance-Space-Id` | 当前实例空间 ID。 |
| `X-Stellorbit-Operator` | 当前操作人，会写入创建人、更新人、发布人和审计字段。 |
| `X-Stellorbit-Roles` | 逗号分隔角色，例如 `ADMIN`、`OPERATOR`、`PUBLISHER`、`APPROVER`。 |
| `X-Stellorbit-Reason` | 控制面写操作原因。 |
| `X-Request-Id` | 请求链路 ID。 |

运行时同步接口与控制面权限模型隔离，避免数据面客户端依赖交互式操作人权限。

## 项目结构

```text
src/main/java/io/github/stellorbit
  api/                 REST 控制器、DTO、错误处理和安全钩子
  application/         用例、服务、发布 worker 和端口
  domain/              共享领域工具
  infrastructure/      CUE、持久化、Stellnula 和运行时集成
src/main/resources     Spring Boot 配置
src/test/java          单元测试
docs/                  领域设计文档
schema.sql             PostgreSQL Schema
data.sql               本地/测试种子数据
drop.sql               本地/测试库重建辅助脚本
```

## 环境要求

- JDK 25，或兼容 `maven.compiler.release=25` 的工具链。
- Maven 3.9+。
- PostgreSQL。
- CUE CLI，可通过 `cue` 命令访问，或使用 `STELLORBIT_CUE_BINARY` 指定路径。
- 可访问的 `stellnula-service` 实例，用于发布集成链路。

## 配置

默认配置位于 [src/main/resources/application.yml](src/main/resources/application.yml)。

常用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `STELLORBIT_DATASOURCE_URL` | `jdbc:postgresql://192.168.1.14:5432/stellorbit` | PostgreSQL JDBC 地址。 |
| `STELLORBIT_DATASOURCE_USERNAME` | `stellhub` | 数据库用户名。 |
| `STELLORBIT_DATASOURCE_PASSWORD` | `admin` | 数据库密码。 |
| `STELLORBIT_STELLNULA_BASE_URL` | `http://127.0.0.1:8060` | 配置中心地址。 |
| `STELLORBIT_CUE_BINARY` | `cue` | CUE 可执行文件路径。 |
| `STELLORBIT_PUBLISH_WORKER_ENABLED` | `true` | 是否启用异步发布 worker。 |
| `STELLFLUX_OTEL_ENABLED` | `true` | 是否启用 Stellflux OpenTelemetry 集成。 |

## 本地开发

初始化数据库：

```bash
psql "$STELLORBIT_DATASOURCE_URL" -f schema.sql
```

可选：重建本地测试数据库并写入种子数据：

```bash
psql "$STELLORBIT_DATASOURCE_URL" -f drop.sql
psql "$STELLORBIT_DATASOURCE_URL" -f schema.sql
psql "$STELLORBIT_DATASOURCE_URL" -f data.sql
```

运行测试：

```bash
mvn test
```

检查格式：

```bash
mvn spotless:check
```

启动服务：

```bash
mvn spring-boot:run
```

健康检查：

```text
GET /actuator/health
```

## 文档

- [鉴权规则模型](docs/auth.md)
- [路由规则模型](docs/router.md)
- [熔断规则模型](docs/breaker.md)
- [限流规则模型](docs/ratelimiter.md)

## License

本项目使用 Apache License, Version 2.0。

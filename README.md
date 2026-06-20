# Stellorbit Service

[English](README.md) | [简体中文](README_CN.md)

Stellorbit Service is the governance rule data-plane service for the StellHub
ecosystem. It stores typed governance rules, validates CUE-based rule sources,
compiles stable runtime snapshots, and publishes those snapshots to
`stellnula-service` so clients and independent runtime components can consume
governance configuration consistently.

The project is designed around a clear separation of concerns:

- Stellorbit Service owns rule modeling, validation, release orchestration,
  runtime snapshot generation, audit data, and control-plane APIs.
- `stellnula-service` owns configuration storage, versioning, watch delivery,
  and downstream distribution.
- Independent runtime components own data-plane decisions such as distributed
  rate limiting, quota allocation, lease renewal, usage reporting, hot-key
  sharding, and rebalancing.

## Why Stellorbit Service Exists

Service governance rules are not simple key-value documents. Routing, circuit
breaking, rate limiting, and authorization all need typed fields, lifecycle
metadata, schema validation, conflict detection, release history, and runtime
delivery semantics.

Stellorbit Service therefore uses a "shared rule header + typed rule detail"
model:

```text
governance_rules
  |-- route_rules
  |-- breaker_rules
  |-- rate_limit_rules
  `-- auth_policy_rules
```

`governance_rules` stores common lifecycle data such as rule code, rule name,
rule type, source format, runtime snapshot, status, version, and release
relationship. Each typed detail table stores the rule semantics that must remain
queryable, validated, and easy to render in a control-plane UI.

## Core Capabilities

| Capability | Description |
| --- | --- |
| Typed governance model | Dedicated models for routing, circuit breaking, rate limiting, and authorization rules. |
| CUE validation pipeline | Rule source validation, default merging, JSON export, checksum generation, conflict detection, and compatibility checks. |
| Release orchestration | Versioned releases, release items, publish records, async publish jobs, publish locks, retry, recovery, and rollback. |
| Configuration-center publishing | Publishes governance snapshots, mTLS certificates, JWKS payloads, and rate-limit configurations to `stellnula-service`. |
| Runtime delivery APIs | Client-facing snapshot, watch, ACK, status report, heartbeat, and instance-view APIs. |
| Distributed rate-limit sync | Startup full snapshot plus runtime incremental synchronization for independent distributed rate-limit servers. |
| Security and audit | Control-plane headers, RBAC conventions, operation reasons, release approval records, and immutable audit events. |
| Observability | Spring Boot Actuator plus Stellflux HTTP, logging, metrics, traces, and Caffeine telemetry integration. |

## Architecture

| Component | Responsibility |
| --- | --- |
| Governance control plane | User-facing API and console for instance spaces, applications, rules, releases, approvals, and rollback. |
| Stellorbit Service | Rule data-plane service that persists, validates, compiles, releases, and exposes runtime snapshots. |
| `stellnula-service` | Configuration center used for published runtime configuration storage, versioning, watch, and delivery. |
| Independent rate-limit runtime | Executes distributed rate-limit decisions, quota coordination, usage reporting, and rebalancing. |
| Stellorbit Client | Runtime governance engine that consumes snapshots and applies routing, breaker, auth, and local rate-limit policies. |

## Rule Domains

### Routing

Routing rules model capabilities similar to Istio `VirtualService`,
`DestinationRule`, `Gateway`, and Envoy routing:

- HTTP, TCP, TLS/SNI routing.
- Weighted traffic splitting and canary routing.
- Header, query, cookie, API, host, namespace, and service matching.
- Request mirroring, rewrite, redirect, direct response, and fault injection.
- Timeout, retry, load balancing, failover, and egress governance options.

See [docs/router.md](docs/router.md).

### Circuit Breaking

Circuit breaker rules model connection pools, resource limits, outlier
detection, sliding-window failure thresholds, slow-call thresholds, half-open
probes, retry budgets, fallback policies, and exception classification.

See [docs/breaker.md](docs/breaker.md).

### Rate Limiting

Rate-limit rules describe what should be limited and where the rule should be
executed. Stellorbit Service does not perform runtime rate-limit decisions.

The model separates:

- `execution_location`: `APPLICATION`, `SIDECAR`, `GATEWAY`, or `EDGE`.
- `coordination_mode`: `LOCAL_ONLY`, `GLOBAL_SYNC`, or `GLOBAL_QUOTA`.
- `enforcement_mode`: legacy compatibility field derived from the two fields
  above.

Distributed rate-limit rules are exported to independent rate-limit servers with
the same payload shape used for configuration-center publishing.

See [docs/ratelimiter.md](docs/ratelimiter.md).

### Authorization

Authorization rules follow the conceptual model of Istio security resources:

- `PeerAuthentication` for mTLS posture.
- `RequestAuthentication` for JWT issuers, audiences, JWKS, claims, and token
  extraction.
- `AuthorizationPolicy` for `CUSTOM`, `DENY`, `ALLOW`, and `AUDIT` decisions.

Stellorbit Service also models mTLS certificates, certificate bindings, JWKS
publishing, certificate validity, and delivery records.

See [docs/auth.md](docs/auth.md).

## CUE Compilation Pipeline

Stellorbit Service stores user-authored CUE sources and compiles them into
stable JSON runtime snapshots before publishing:

1. Load the active CUE schema version for the rule type.
2. Merge typed detail-table data, the submitted CUE source, and schema defaults.
3. Run `cue export` to materialize normalized JSON.
4. Generate deterministic checksums from normalized content.
5. Run semantic conflict detection and compatibility validation.
6. Return dry-run diagnostics or create release records.

The runtime format is intentionally stable:

```text
CUE source -> schema/default merge -> JSON snapshot -> stellnula-service -> runtime clients
```

## Runtime Delivery

Stellorbit clients use a dedicated runtime protocol instead of reading
control-plane tables directly:

```text
POST /api/stellorbit/runtime/rules/negotiate
GET  /api/stellorbit/runtime/rules/snapshot
GET  /api/stellorbit/runtime/rules/watch
POST /api/stellorbit/runtime/rules/acks
POST /api/stellorbit/runtime/rules/status-reports
POST /api/stellorbit/runtime/rules/heartbeats
GET  /api/stellorbit/runtime/rules/instances
```

The runtime snapshot schema is currently `stellorbit.runtime.snapshot.v1`, and
the protocol version is `stellorbit.runtime.protocol.v1`.

## Distributed Rate-Limit Synchronization

Independent distributed rate-limit servers should not repeatedly pull full
snapshots after startup. The intended flow is:

1. On startup, pull all distributed rate-limit configurations with pagination:

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/snapshot?page=0&size=100
   ```

2. During normal operation, pull incremental changes from the local watermark:

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/changes?currentSnapshotVersion=1&currentChecksum=...
   ```

3. Keep an SSE watch open for timely incremental delivery:

   ```text
   GET /api/stellorbit/runtime/distributed-rate-limits/watch?currentSnapshotVersion=1&currentChecksum=...
   ```

Incremental changes are emitted at the configuration-unit level:

- `UPSERT_CONFIG`: create or replace the local config identified by `configId`.
- `DELETE_CONFIG`: remove the local config identified by `configId`.
- `FULL_SYNC_REQUIRED`: the caller's watermark cannot be reconciled with the
  local change log, so it must run a paged full sync again.

The in-memory snapshot is backed by Stellflux Caffeine telemetry and keeps a
bounded change log for incremental catch-up.

## Release and Publishing Model

Releases are executed through an outbox/job model instead of calling the
configuration center directly in the HTTP request thread:

1. A release creates `rule_releases`, `release_items`,
   `stellnula_publish_records`, and `publish_jobs`.
2. Publish locks serialize releases at the application level.
3. The publish worker retries failed jobs with idempotency keys.
4. Reconciliation reads back already-successful publishes from
   `stellnula-service`.
5. Final release status becomes `PUBLISHED`, `PARTIAL_PUBLISHED`, or `FAILED`.

## Control-Plane Security Headers

Control-plane write APIs expect the following headers so Stellorbit Service can
enforce tenancy, RBAC conventions, release accountability, and audit trails:

| Header | Description |
| --- | --- |
| `X-Stellorbit-Tenant-Id` | Tenant identifier used for tenant-scoped data access. |
| `X-Stellorbit-Instance-Space-Id` | Instance-space identifier for control-plane operations. |
| `X-Stellorbit-Operator` | Operator identity written to created/updated/published/audit fields. |
| `X-Stellorbit-Roles` | Comma-separated roles such as `ADMIN`, `OPERATOR`, `PUBLISHER`, and `APPROVER`. |
| `X-Stellorbit-Reason` | Required reason for mutating control-plane operations. |
| `X-Request-Id` | Request correlation identifier for tracing and auditing. |

Runtime synchronization endpoints are intentionally separated from this
control-plane security model so data-plane clients are not coupled to
interactive operator permissions.

## Project Layout

```text
src/main/java/io/github/stellorbit
  api/                 REST controllers, DTOs, errors, and security hooks
  application/         Use cases, services, publish worker, and ports
  domain/              Shared domain helpers
  infrastructure/      CUE, persistence, Stellnula, and runtime integration
src/main/resources     Spring Boot configuration
src/test/java          Unit tests
docs/                  Domain design notes
schema.sql             PostgreSQL schema
data.sql               Local/test seed data
drop.sql               Local/test database rebuild helper
```

## Requirements

- JDK 25 or a compatible toolchain for `maven.compiler.release=25`.
- Maven 3.9+.
- PostgreSQL.
- CUE CLI available as `cue`, or configured through `STELLORBIT_CUE_BINARY`.
- A reachable `stellnula-service` instance for publish integration paths.

## Configuration

The default application configuration is in
[src/main/resources/application.yml](src/main/resources/application.yml).

Common environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `STELLORBIT_DATASOURCE_URL` | `jdbc:postgresql://192.168.1.14:5432/stellorbit` | PostgreSQL JDBC URL. |
| `STELLORBIT_DATASOURCE_USERNAME` | `stellhub` | Database username. |
| `STELLORBIT_DATASOURCE_PASSWORD` | `admin` | Database password. |
| `STELLORBIT_STELLNULA_BASE_URL` | `http://127.0.0.1:8060` | Configuration-center base URL. |
| `STELLORBIT_CUE_BINARY` | `cue` | CUE executable path. |
| `STELLORBIT_PUBLISH_WORKER_ENABLED` | `true` | Enables the asynchronous publish worker. |
| `STELLFLUX_OTEL_ENABLED` | `true` | Enables Stellflux OpenTelemetry integration. |

## Local Development

Prepare the database schema:

```bash
psql "$STELLORBIT_DATASOURCE_URL" -f schema.sql
```

Optionally rebuild a local test database and insert sample data:

```bash
psql "$STELLORBIT_DATASOURCE_URL" -f drop.sql
psql "$STELLORBIT_DATASOURCE_URL" -f schema.sql
psql "$STELLORBIT_DATASOURCE_URL" -f data.sql
```

Run tests:

```bash
mvn test
```

Check formatting:

```bash
mvn spotless:check
```

Start the service:

```bash
mvn spring-boot:run
```

Check service health:

```text
GET /actuator/health
```

## Documentation

- [Authorization model](docs/auth.md)
- [Routing model](docs/router.md)
- [Circuit breaker model](docs/breaker.md)
- [Rate limiter model](docs/ratelimiter.md)

## License

This project is licensed under the Apache License, Version 2.0.

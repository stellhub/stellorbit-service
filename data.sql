-- Seed data for local/test databases.
-- Creates one instance space, two applications, and 10 rules for each rule type.
BEGIN;

INSERT INTO instance_spaces (
    id,
    tenant_id,
    space_code,
    space_name,
    organization,
    business_domain,
    capability_domain,
    environment,
    description,
    labels,
    status,
    created_by,
    updated_by
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'tenant-demo',
    'demo-space',
    'Demo Instance Space',
    'StellHub',
    'commerce',
    'service-governance',
    'test',
    'Seed data instance space for local and integration tests.',
    '{"env":"test","seed":true}'::jsonb,
    'ACTIVE',
    'data.sql',
    'data.sql'
)
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    space_code = EXCLUDED.space_code,
    space_name = EXCLUDED.space_name,
    organization = EXCLUDED.organization,
    business_domain = EXCLUDED.business_domain,
    capability_domain = EXCLUDED.capability_domain,
    environment = EXCLUDED.environment,
    description = EXCLUDED.description,
    labels = EXCLUDED.labels,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

INSERT INTO applications (
    id,
    tenant_id,
    instance_space_id,
    application_code,
    application_name,
    owner_team,
    description,
    labels,
    status,
    created_by,
    updated_by
)
VALUES
(
    '00000000-0000-0000-0000-000000000101',
    'tenant-demo',
    '00000000-0000-0000-0000-000000000001',
    'order-service',
    'Order Service',
    'commerce-platform',
    'Sample order application for Stellorbit governance rules.',
    '{"tier":"core","seed":true}'::jsonb,
    'ACTIVE',
    'data.sql',
    'data.sql'
),
(
    '00000000-0000-0000-0000-000000000102',
    'tenant-demo',
    '00000000-0000-0000-0000-000000000001',
    'payment-service',
    'Payment Service',
    'commerce-platform',
    'Sample payment application for Stellorbit governance rules.',
    '{"tier":"core","seed":true}'::jsonb,
    'ACTIVE',
    'data.sql',
    'data.sql'
)
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    instance_space_id = EXCLUDED.instance_space_id,
    application_code = EXCLUDED.application_code,
    application_name = EXCLUDED.application_name,
    owner_team = EXCLUDED.owner_team,
    description = EXCLUDED.description,
    labels = EXCLUDED.labels,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

WITH seed AS (
    SELECT
        generate_series(1, 10) AS i,
        'tenant-demo'::varchar AS tenant_id,
        '00000000-0000-0000-0000-000000000001'::uuid AS space_id,
        '00000000-0000-0000-0000-000000000101'::uuid AS order_app_id,
        '00000000-0000-0000-0000-000000000102'::uuid AS payment_app_id
),
route_rows AS (
    SELECT
        ('10000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
        tenant_id,
        space_id,
        CASE WHEN i <= 5 THEN order_app_id ELSE payment_app_id END AS application_id,
        CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END AS application_code,
        i,
        lpad(i::text, 2, '0') AS n
    FROM seed
)
INSERT INTO governance_rules (
    id,
    tenant_id,
    instance_space_id,
    application_id,
    rule_code,
    rule_name,
    rule_type,
    source_format,
    runtime_format,
    cue_source,
    runtime_snapshot_json,
    checksum,
    priority,
    enabled,
    status,
    draft_version,
    description,
    tags,
    created_by,
    updated_by
)
SELECT
    id,
    tenant_id,
    space_id,
    application_id,
    'route-demo-' || n,
    'Route Demo ' || n,
    'ROUTE',
    'CUE',
    'JSON',
    'rule: {}',
    jsonb_build_object('targetService', application_code, 'sampleIndex', i),
    md5('route-demo-' || n),
    100 + i,
    TRUE,
    'DRAFT',
    1,
    'Seed route governance rule ' || n,
    jsonb_build_array('seed', 'route'),
    'data.sql',
    'data.sql'
FROM route_rows
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    instance_space_id = EXCLUDED.instance_space_id,
    application_id = EXCLUDED.application_id,
    rule_code = EXCLUDED.rule_code,
    rule_name = EXCLUDED.rule_name,
    rule_type = EXCLUDED.rule_type,
    source_format = EXCLUDED.source_format,
    runtime_format = EXCLUDED.runtime_format,
    cue_source = EXCLUDED.cue_source,
    runtime_snapshot_json = EXCLUDED.runtime_snapshot_json,
    checksum = EXCLUDED.checksum,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    status = EXCLUDED.status,
    draft_version = EXCLUDED.draft_version,
    description = EXCLUDED.description,
    tags = EXCLUDED.tags,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

WITH route_rows AS (
    SELECT
        generate_series(1, 10) AS i
)
INSERT INTO route_rules (
    governance_rule_id,
    route_type,
    traffic_direction,
    protocol,
    gateways,
    hosts,
    source_selector,
    match_conditions,
    destinations,
    route_action,
    rewrite_policy,
    redirect_policy,
    mirror_policy,
    fault_injection_policy,
    timeout_policy,
    retry_policy,
    load_balance_policy,
    locality_policy
)
SELECT
    ('10000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
    (ARRAY['HTTP','CANARY','HEADER','QUERY','COOKIE','TRAFFIC_SPLIT','LOCALITY','FAILOVER','MIRROR','EGRESS'])[((i - 1) % 10) + 1],
    CASE WHEN i IN (1, 2, 3, 4) THEN 'NORTH_SOUTH' WHEN i IN (10) THEN 'EGRESS' ELSE 'EAST_WEST' END,
    'HTTP',
    jsonb_build_array('public-gateway'),
    jsonb_build_array('api' || i || '.demo.stellhub.local'),
    jsonb_build_object('namespace', 'demo', 'app', CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END),
    jsonb_build_array(jsonb_build_object('pathPrefix', '/api/v1/demo/' || i, 'method', 'GET')),
    jsonb_build_array(jsonb_build_object('service', CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END, 'subset', 'v1', 'weight', 100)),
    jsonb_build_object('type', 'FORWARD'),
    jsonb_build_object('prefixRewrite', '/internal/demo/' || i),
    '{}'::jsonb,
    jsonb_build_object('enabled', i = 9, 'percentage', 5),
    jsonb_build_object('abortPercentage', 0),
    jsonb_build_object('requestTimeoutMillis', 2000 + i * 100),
    jsonb_build_object('attempts', 2, 'perTryTimeoutMillis', 500),
    jsonb_build_object('strategy', 'ROUND_ROBIN'),
    jsonb_build_object('preferLocalZone', TRUE)
FROM route_rows
ON CONFLICT (governance_rule_id) DO UPDATE SET
    route_type = EXCLUDED.route_type,
    traffic_direction = EXCLUDED.traffic_direction,
    protocol = EXCLUDED.protocol,
    gateways = EXCLUDED.gateways,
    hosts = EXCLUDED.hosts,
    source_selector = EXCLUDED.source_selector,
    match_conditions = EXCLUDED.match_conditions,
    destinations = EXCLUDED.destinations,
    route_action = EXCLUDED.route_action,
    rewrite_policy = EXCLUDED.rewrite_policy,
    redirect_policy = EXCLUDED.redirect_policy,
    mirror_policy = EXCLUDED.mirror_policy,
    fault_injection_policy = EXCLUDED.fault_injection_policy,
    timeout_policy = EXCLUDED.timeout_policy,
    retry_policy = EXCLUDED.retry_policy,
    load_balance_policy = EXCLUDED.load_balance_policy,
    locality_policy = EXCLUDED.locality_policy,
    updated_at = NOW();

WITH seed AS (
    SELECT
        generate_series(1, 10) AS i,
        'tenant-demo'::varchar AS tenant_id,
        '00000000-0000-0000-0000-000000000001'::uuid AS space_id,
        '00000000-0000-0000-0000-000000000101'::uuid AS order_app_id,
        '00000000-0000-0000-0000-000000000102'::uuid AS payment_app_id
),
breaker_rows AS (
    SELECT
        ('20000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
        tenant_id,
        space_id,
        CASE WHEN i <= 5 THEN order_app_id ELSE payment_app_id END AS application_id,
        CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END AS application_code,
        i,
        lpad(i::text, 2, '0') AS n
    FROM seed
)
INSERT INTO governance_rules (
    id,
    tenant_id,
    instance_space_id,
    application_id,
    rule_code,
    rule_name,
    rule_type,
    source_format,
    runtime_format,
    cue_source,
    runtime_snapshot_json,
    checksum,
    priority,
    enabled,
    status,
    draft_version,
    description,
    tags,
    created_by,
    updated_by
)
SELECT
    id,
    tenant_id,
    space_id,
    application_id,
    'breaker-demo-' || n,
    'Breaker Demo ' || n,
    'BREAKER',
    'CUE',
    'JSON',
    'rule: {}',
    jsonb_build_object('targetService', application_code, 'sampleIndex', i),
    md5('breaker-demo-' || n),
    200 + i,
    TRUE,
    'DRAFT',
    1,
    'Seed breaker governance rule ' || n,
    jsonb_build_array('seed', 'breaker'),
    'data.sql',
    'data.sql'
FROM breaker_rows
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    instance_space_id = EXCLUDED.instance_space_id,
    application_id = EXCLUDED.application_id,
    rule_code = EXCLUDED.rule_code,
    rule_name = EXCLUDED.rule_name,
    rule_type = EXCLUDED.rule_type,
    source_format = EXCLUDED.source_format,
    runtime_format = EXCLUDED.runtime_format,
    cue_source = EXCLUDED.cue_source,
    runtime_snapshot_json = EXCLUDED.runtime_snapshot_json,
    checksum = EXCLUDED.checksum,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    status = EXCLUDED.status,
    draft_version = EXCLUDED.draft_version,
    description = EXCLUDED.description,
    tags = EXCLUDED.tags,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

WITH breaker_rows AS (
    SELECT generate_series(1, 10) AS i
)
INSERT INTO breaker_rules (
    governance_rule_id,
    breaker_type,
    protocol,
    target_selector,
    window_type,
    window_size,
    minimum_calls,
    failure_rate_threshold,
    slow_call_rate_threshold,
    slow_call_duration_millis,
    open_state_wait_millis,
    permitted_half_open_calls,
    connection_pool_policy,
    outlier_detection_policy,
    retry_budget_policy,
    exception_record_policy,
    exception_ignore_policy,
    fallback_policy
)
SELECT
    ('20000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
    (ARRAY['CONNECTION_POOL','CONSECUTIVE_ERROR','ERROR_RATE','SLOW_CALL_RATE','EXCEPTION_CLASSIFICATION','OUTLIER_DETECTION','HALF_OPEN_PROBE','RETRY_BUDGET','BULKHEAD','ERROR_RATE'])[((i - 1) % 10) + 1],
    CASE WHEN i IN (1, 8) THEN 'HTTP2' WHEN i = 2 THEN 'TCP' ELSE 'HTTP' END,
    jsonb_build_object('service', CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END, 'endpoint', '/api/v1/demo/' || i),
    CASE WHEN i % 2 = 0 THEN 'TIME_BASED' ELSE 'COUNT_BASED' END,
    50 + i,
    10 + i,
    40 + i,
    50 + i,
    300 + i * 10,
    5000 + i * 100,
    2 + i,
    jsonb_build_object('maxConnections', 100 + i, 'maxPendingRequests', 50 + i),
    jsonb_build_object('consecutiveErrors', 5, 'baseEjectionMillis', 30000),
    jsonb_build_object('maxRetryRatio', 0.2, 'minRetryConcurrency', 5),
    jsonb_build_object('record', jsonb_build_array('java.io.IOException', 'java.util.concurrent.TimeoutException')),
    jsonb_build_object('ignore', jsonb_build_array('java.lang.IllegalArgumentException')),
    jsonb_build_object('strategy', 'STATIC_RESPONSE', 'status', 503)
FROM breaker_rows
ON CONFLICT (governance_rule_id) DO UPDATE SET
    breaker_type = EXCLUDED.breaker_type,
    protocol = EXCLUDED.protocol,
    target_selector = EXCLUDED.target_selector,
    window_type = EXCLUDED.window_type,
    window_size = EXCLUDED.window_size,
    minimum_calls = EXCLUDED.minimum_calls,
    failure_rate_threshold = EXCLUDED.failure_rate_threshold,
    slow_call_rate_threshold = EXCLUDED.slow_call_rate_threshold,
    slow_call_duration_millis = EXCLUDED.slow_call_duration_millis,
    open_state_wait_millis = EXCLUDED.open_state_wait_millis,
    permitted_half_open_calls = EXCLUDED.permitted_half_open_calls,
    connection_pool_policy = EXCLUDED.connection_pool_policy,
    outlier_detection_policy = EXCLUDED.outlier_detection_policy,
    retry_budget_policy = EXCLUDED.retry_budget_policy,
    exception_record_policy = EXCLUDED.exception_record_policy,
    exception_ignore_policy = EXCLUDED.exception_ignore_policy,
    fallback_policy = EXCLUDED.fallback_policy,
    updated_at = NOW();

WITH seed AS (
    SELECT
        generate_series(1, 10) AS i,
        'tenant-demo'::varchar AS tenant_id,
        '00000000-0000-0000-0000-000000000001'::uuid AS space_id,
        '00000000-0000-0000-0000-000000000101'::uuid AS order_app_id,
        '00000000-0000-0000-0000-000000000102'::uuid AS payment_app_id
),
rate_rows AS (
    SELECT
        ('30000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
        tenant_id,
        space_id,
        CASE WHEN i <= 5 THEN order_app_id ELSE payment_app_id END AS application_id,
        CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END AS application_code,
        i,
        lpad(i::text, 2, '0') AS n
    FROM seed
)
INSERT INTO governance_rules (
    id,
    tenant_id,
    instance_space_id,
    application_id,
    rule_code,
    rule_name,
    rule_type,
    source_format,
    runtime_format,
    cue_source,
    runtime_snapshot_json,
    checksum,
    priority,
    enabled,
    status,
    draft_version,
    description,
    tags,
    created_by,
    updated_by
)
SELECT
    id,
    tenant_id,
    space_id,
    application_id,
    'rate-limit-demo-' || n,
    'Rate Limit Demo ' || n,
    'RATE_LIMIT',
    'CUE',
    'JSON',
    'rule: {}',
    jsonb_build_object('targetService', application_code, 'sampleIndex', i),
    md5('rate-limit-demo-' || n),
    300 + i,
    TRUE,
    'DRAFT',
    1,
    'Seed rate limit governance rule ' || n,
    jsonb_build_array('seed', 'rate-limit'),
    'data.sql',
    'data.sql'
FROM rate_rows
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    instance_space_id = EXCLUDED.instance_space_id,
    application_id = EXCLUDED.application_id,
    rule_code = EXCLUDED.rule_code,
    rule_name = EXCLUDED.rule_name,
    rule_type = EXCLUDED.rule_type,
    source_format = EXCLUDED.source_format,
    runtime_format = EXCLUDED.runtime_format,
    cue_source = EXCLUDED.cue_source,
    runtime_snapshot_json = EXCLUDED.runtime_snapshot_json,
    checksum = EXCLUDED.checksum,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    status = EXCLUDED.status,
    draft_version = EXCLUDED.draft_version,
    description = EXCLUDED.description,
    tags = EXCLUDED.tags,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

WITH rate_modes AS (
    SELECT
        i,
        CASE
            WHEN i = 2 THEN 'EDGE'
            WHEN i IN (4, 8, 10) THEN 'GATEWAY'
            WHEN i IN (7, 9) THEN 'SIDECAR'
            ELSE 'APPLICATION'
        END AS execution_location,
        CASE
            WHEN i IN (3, 4, 9) THEN 'GLOBAL_SYNC'
            WHEN i IN (5, 6, 10) THEN 'GLOBAL_QUOTA'
            ELSE 'LOCAL_ONLY'
        END AS coordination_mode
    FROM generate_series(1, 10) AS i
),
rate_rows AS (
    SELECT
        i,
        execution_location,
        coordination_mode,
        CASE
            WHEN coordination_mode IN ('GLOBAL_SYNC', 'GLOBAL_QUOTA') THEN coordination_mode
            WHEN execution_location = 'EDGE' THEN 'EDGE'
            ELSE 'LOCAL'
        END AS enforcement_mode
    FROM rate_modes
)
INSERT INTO rate_limit_rules (
    governance_rule_id,
    limit_mode,
    limit_type,
    limit_algorithm,
    traffic_protocol,
    enforcement_mode,
    execution_location,
    coordination_mode,
    target_selector,
    request_matcher,
    key_extractor,
    dimensions,
    quota_config,
    window_config,
    burst_config,
    concurrency_config,
    hotspot_config,
    custom_policy,
    model_limit_config,
    fallback_policy,
    response_policy,
    observability_config,
    shadow_config
)
SELECT
    ('30000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
    (ARRAY['QPS','CONCURRENCY','HEADER','HOT_KEY','CUSTOM','QUOTA','BANDWIDTH','CONNECTION','MODEL','MODEL'])[((i - 1) % 10) + 1],
    (ARRAY['REQUEST','CONNECTION','HEADER','RESOURCE','CUSTOM_KEY','TENANT','BYTE','CONNECTION','MODEL_REQUEST','MODEL_TOKEN'])[((i - 1) % 10) + 1],
    (ARRAY['TOKEN_BUCKET','CONCURRENCY_LIMIT','SLIDING_WINDOW','HOT_KEY','CUSTOM','QUOTA_LEASE','LEAKY_BUCKET','CONCURRENCY_LIMIT','TOKEN_BUCKET','SLIDING_WINDOW'])[((i - 1) % 10) + 1],
    (ARRAY['HTTP','HTTP','GRPC','HTTP','ANY','ANY','HTTP','TCP','MODEL','MODEL'])[((i - 1) % 10) + 1],
    enforcement_mode,
    execution_location,
    coordination_mode,
    jsonb_build_object('service', CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END, 'path', '/api/v1/demo/' || i),
    jsonb_build_object(
        'http', jsonb_build_object('methods', jsonb_build_array('GET', 'POST'), 'pathPattern', '/api/v1/demo/{id}'),
        'grpc', jsonb_build_object('service', 'demo.RateLimitService', 'method', 'Check' || i)
    ),
    jsonb_build_object(
        'strategy', 'COMPOSITE',
        'failOnMissing', false,
        'keys', jsonb_build_array(
            jsonb_build_object('name', 'tenant', 'source', CASE WHEN i = 3 THEN 'GRPC_METADATA' ELSE 'HEADER' END, 'key', 'x-tenant-id', 'required', true),
            jsonb_build_object('name', 'api', 'source', CASE WHEN i = 3 THEN 'GRPC_METHOD' ELSE 'HTTP_PATH' END, 'key', 'pathTemplate')
        )
    ),
    jsonb_build_array(
        jsonb_build_object('name', 'tenant', 'source', CASE WHEN i = 3 THEN 'GRPC_METADATA' ELSE 'HEADER' END, 'key', 'x-tenant-id'),
        jsonb_build_object('name', 'api', 'source', CASE WHEN i = 3 THEN 'GRPC_METHOD' ELSE 'HTTP_PATH' END, 'key', 'pathTemplate')
    ),
    jsonb_build_object('limit', 1000 + i * 100, 'unit', CASE WHEN i = 7 THEN 'BYTE' ELSE 'REQUEST' END, 'period', 'SECOND', 'scope', 'PER_KEY'),
    jsonb_build_object('windowType', 'SLIDING', 'durationMillis', 60000, 'bucketCount', 60, 'refillSeconds', 1),
    jsonb_build_object('capacity', 100 + i * 10, 'refillRate', 1000 + i * 100, 'maxBurstRatio', 1.5),
    jsonb_build_object('maxConcurrent', 50 + i, 'queueLimit', 100, 'queueTimeoutMillis', 200, 'releaseOn', 'RESPONSE_COMPLETED'),
    jsonb_build_object('enabled', true, 'metric', 'QPS', 'topN', 100, 'threshold', 1000 + i * 100, 'ttlMillis', 60000),
    jsonb_build_object('policyType', 'EXPRESSION', 'language', 'CEL', 'expression', 'request.tenant + ":" + request.path', 'timeoutMillis', 20, 'failPolicy', 'FAIL_OPEN'),
    jsonb_build_object('provider', 'openai-compatible', 'model', 'demo-model-' || i, 'tokenLimit', 10000 + i * 1000),
    jsonb_build_object('mode', CASE WHEN i % 2 = 0 THEN 'FAIL_OPEN' ELSE 'FAIL_CLOSED' END),
    jsonb_build_object('httpStatus', 429, 'grpcStatus', 'RESOURCE_EXHAUSTED', 'message', 'too many requests'),
    jsonb_build_object('metricLabels', jsonb_build_array('tenant', 'api'), 'logRejected', true, 'sampleAllowedRatio', 0.01),
    jsonb_build_object('enabled', i = 5, 'mode', 'DRY_RUN')
FROM rate_rows
ON CONFLICT (governance_rule_id) DO UPDATE SET
    limit_mode = EXCLUDED.limit_mode,
    limit_type = EXCLUDED.limit_type,
    limit_algorithm = EXCLUDED.limit_algorithm,
    traffic_protocol = EXCLUDED.traffic_protocol,
    enforcement_mode = EXCLUDED.enforcement_mode,
    execution_location = EXCLUDED.execution_location,
    coordination_mode = EXCLUDED.coordination_mode,
    target_selector = EXCLUDED.target_selector,
    request_matcher = EXCLUDED.request_matcher,
    key_extractor = EXCLUDED.key_extractor,
    dimensions = EXCLUDED.dimensions,
    quota_config = EXCLUDED.quota_config,
    window_config = EXCLUDED.window_config,
    burst_config = EXCLUDED.burst_config,
    concurrency_config = EXCLUDED.concurrency_config,
    hotspot_config = EXCLUDED.hotspot_config,
    custom_policy = EXCLUDED.custom_policy,
    model_limit_config = EXCLUDED.model_limit_config,
    fallback_policy = EXCLUDED.fallback_policy,
    response_policy = EXCLUDED.response_policy,
    observability_config = EXCLUDED.observability_config,
    shadow_config = EXCLUDED.shadow_config,
    updated_at = NOW();

WITH seed AS (
    SELECT
        generate_series(1, 10) AS i,
        'tenant-demo'::varchar AS tenant_id,
        '00000000-0000-0000-0000-000000000001'::uuid AS space_id,
        '00000000-0000-0000-0000-000000000101'::uuid AS order_app_id,
        '00000000-0000-0000-0000-000000000102'::uuid AS payment_app_id
),
auth_rows AS (
    SELECT
        ('40000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid AS id,
        tenant_id,
        space_id,
        CASE WHEN i <= 5 THEN order_app_id ELSE payment_app_id END AS application_id,
        CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END AS application_code,
        i,
        lpad(i::text, 2, '0') AS n
    FROM seed
)
INSERT INTO governance_rules (
    id,
    tenant_id,
    instance_space_id,
    application_id,
    rule_code,
    rule_name,
    rule_type,
    source_format,
    runtime_format,
    cue_source,
    runtime_snapshot_json,
    checksum,
    priority,
    enabled,
    status,
    draft_version,
    description,
    tags,
    created_by,
    updated_by
)
SELECT
    id,
    tenant_id,
    space_id,
    application_id,
    'auth-demo-' || n,
    'Auth Demo ' || n,
    'AUTH',
    'CUE',
    'JSON',
    'rule: {}',
    jsonb_build_object('targetService', application_code, 'sampleIndex', i),
    md5('auth-demo-' || n),
    400 + i,
    TRUE,
    'DRAFT',
    1,
    'Seed auth governance rule ' || n,
    jsonb_build_array('seed', 'auth'),
    'data.sql',
    'data.sql'
FROM auth_rows
ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    instance_space_id = EXCLUDED.instance_space_id,
    application_id = EXCLUDED.application_id,
    rule_code = EXCLUDED.rule_code,
    rule_name = EXCLUDED.rule_name,
    rule_type = EXCLUDED.rule_type,
    source_format = EXCLUDED.source_format,
    runtime_format = EXCLUDED.runtime_format,
    cue_source = EXCLUDED.cue_source,
    runtime_snapshot_json = EXCLUDED.runtime_snapshot_json,
    checksum = EXCLUDED.checksum,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    status = EXCLUDED.status,
    draft_version = EXCLUDED.draft_version,
    description = EXCLUDED.description,
    tags = EXCLUDED.tags,
    updated_by = EXCLUDED.updated_by,
    updated_at = NOW();

WITH auth_rows AS (
    SELECT generate_series(1, 10) AS i
)
INSERT INTO auth_policy_rules (
    governance_rule_id,
    auth_policy_type,
    auth_action,
    mtls_mode,
    trust_domain,
    workload_selector,
    peer_sources,
    request_authentications,
    authorization_from,
    authorization_to,
    authorization_when,
    jwt_rules,
    ext_authz_provider,
    audit_policy
)
SELECT
    ('40000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
    (ARRAY['PEER_AUTHENTICATION','REQUEST_AUTHENTICATION','AUTHORIZATION_POLICY','EXT_AUTHZ','AUTHORIZATION_POLICY','PEER_AUTHENTICATION','REQUEST_AUTHENTICATION','AUTHORIZATION_POLICY','EXT_AUTHZ','AUTHORIZATION_POLICY'])[((i - 1) % 10) + 1],
    CASE
        WHEN i IN (1, 2, 6, 7) THEN NULL
        WHEN i IN (4, 9) THEN 'CUSTOM'
        WHEN i = 5 THEN 'DENY'
        WHEN i = 10 THEN 'AUDIT'
        ELSE 'ALLOW'
    END,
    CASE WHEN i IN (1, 6) THEN 'STRICT' ELSE NULL END,
    'cluster.local',
    jsonb_build_object('service', CASE WHEN i <= 5 THEN 'order-service' ELSE 'payment-service' END),
    jsonb_build_array(jsonb_build_object('principals', jsonb_build_array('cluster.local/ns/demo/sa/client-' || i))),
    jsonb_build_array(jsonb_build_object('issuer', 'https://issuer.demo/' || i, 'audiences', jsonb_build_array('stellhub'))),
    jsonb_build_array(jsonb_build_object('namespaces', jsonb_build_array('demo'), 'principals', jsonb_build_array('*'))),
    jsonb_build_array(jsonb_build_object('methods', jsonb_build_array('GET', 'POST'), 'paths', jsonb_build_array('/api/v1/demo/' || i))),
    jsonb_build_array(jsonb_build_object('key', 'request.auth.claims[tenant]', 'values', jsonb_build_array('tenant-demo'))),
    jsonb_build_array(jsonb_build_object('issuer', 'https://issuer.demo/' || i, 'jwksUri', 'https://issuer.demo/' || i || '/jwks.json')),
    CASE WHEN i IN (4, 9) THEN 'opa-demo-provider' ELSE NULL END,
    jsonb_build_object('enabled', TRUE, 'sampleIndex', i)
FROM auth_rows
ON CONFLICT (governance_rule_id) DO UPDATE SET
    auth_policy_type = EXCLUDED.auth_policy_type,
    auth_action = EXCLUDED.auth_action,
    mtls_mode = EXCLUDED.mtls_mode,
    trust_domain = EXCLUDED.trust_domain,
    workload_selector = EXCLUDED.workload_selector,
    peer_sources = EXCLUDED.peer_sources,
    request_authentications = EXCLUDED.request_authentications,
    authorization_from = EXCLUDED.authorization_from,
    authorization_to = EXCLUDED.authorization_to,
    authorization_when = EXCLUDED.authorization_when,
    jwt_rules = EXCLUDED.jwt_rules,
    ext_authz_provider = EXCLUDED.ext_authz_provider,
    audit_policy = EXCLUDED.audit_policy,
    updated_at = NOW();

COMMIT;

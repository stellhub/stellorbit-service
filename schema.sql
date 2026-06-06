CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS stellorbit;

SET search_path TO stellorbit, public;

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TABLE instance_spaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(120) NOT NULL,
    space_code VARCHAR(160) NOT NULL,
    space_name VARCHAR(160) NOT NULL,
    organization VARCHAR(120) NOT NULL,
    business_domain VARCHAR(120) NOT NULL,
    capability_domain VARCHAR(120) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    description TEXT,
    labels JSONB NOT NULL DEFAULT '{}'::JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_instance_spaces_code UNIQUE (tenant_id, space_code),
    CONSTRAINT ck_instance_spaces_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_instance_spaces_labels_object CHECK (jsonb_typeof(labels) = 'object')
);

COMMENT ON TABLE instance_spaces IS 'Instance spaces for environment, namespace and governance isolation.';

CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_code VARCHAR(160) NOT NULL,
    application_name VARCHAR(160) NOT NULL,
    owner_team VARCHAR(160),
    description TEXT,
    labels JSONB NOT NULL DEFAULT '{}'::JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_applications_code UNIQUE (instance_space_id, application_code),
    CONSTRAINT ck_applications_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_applications_labels_object CHECK (jsonb_typeof(labels) = 'object')
);

COMMENT ON TABLE applications IS 'Governance target applications under an instance space.';

CREATE TABLE governance_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    rule_code VARCHAR(160) NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    rule_type VARCHAR(48) NOT NULL,
    source_format VARCHAR(32) NOT NULL DEFAULT 'CUE',
    runtime_format VARCHAR(32) NOT NULL DEFAULT 'JSON',
    cue_source TEXT NOT NULL,
    runtime_snapshot_json JSONB,
    runtime_snapshot_bytes BYTEA,
    checksum VARCHAR(128),
    priority INTEGER NOT NULL DEFAULT 1000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    draft_version BIGINT NOT NULL DEFAULT 1,
    latest_release_id UUID,
    description TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::JSONB,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_governance_rules_code UNIQUE (instance_space_id, application_id, rule_type, rule_code),
    CONSTRAINT ck_governance_rules_type CHECK (rule_type IN ('ROUTE', 'BREAKER', 'RATE_LIMIT', 'AUTH')),
    CONSTRAINT ck_governance_rules_source_format CHECK (source_format = 'CUE'),
    CONSTRAINT ck_governance_rules_runtime_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_governance_rules_status CHECK (status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_governance_rules_tags_array CHECK (jsonb_typeof(tags) = 'array'),
    CONSTRAINT ck_governance_rules_runtime_snapshot CHECK (
        (runtime_format = 'JSON' AND runtime_snapshot_bytes IS NULL)
        OR (runtime_format = 'PROTOBUF' AND runtime_snapshot_json IS NULL)
    )
);

COMMENT ON TABLE governance_rules IS 'Common rule table for lifecycle, CUE source and runtime snapshot metadata.';

CREATE TABLE cue_schema_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_type VARCHAR(48) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    schema_name VARCHAR(160) NOT NULL,
    cue_schema TEXT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cue_schema_versions_type_version UNIQUE (rule_type, schema_version),
    CONSTRAINT ck_cue_schema_versions_type CHECK (rule_type IN ('ROUTE', 'BREAKER', 'RATE_LIMIT', 'AUTH')),
    CONSTRAINT ck_cue_schema_versions_status CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'))
);

COMMENT ON TABLE cue_schema_versions IS 'Versioned CUE schemas used to validate and normalize governance rules.';

CREATE TABLE route_rules (
    governance_rule_id UUID PRIMARY KEY REFERENCES governance_rules (id) ON DELETE CASCADE,
    route_type VARCHAR(48) NOT NULL,
    traffic_direction VARCHAR(32) NOT NULL DEFAULT 'EAST_WEST',
    protocol VARCHAR(32) NOT NULL,
    gateways JSONB NOT NULL DEFAULT '[]'::JSONB,
    hosts JSONB NOT NULL DEFAULT '[]'::JSONB,
    source_selector JSONB NOT NULL DEFAULT '{}'::JSONB,
    match_conditions JSONB NOT NULL DEFAULT '[]'::JSONB,
    destinations JSONB NOT NULL DEFAULT '[]'::JSONB,
    route_action JSONB NOT NULL DEFAULT '{}'::JSONB,
    rewrite_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    redirect_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    mirror_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    fault_injection_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    timeout_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    retry_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    load_balance_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    locality_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_route_rules_type CHECK (route_type IN ('HTTP', 'GRPC', 'TCP', 'TLS', 'TRAFFIC_SPLIT', 'CANARY', 'HEADER', 'QUERY', 'COOKIE', 'LOCALITY', 'FAILOVER', 'MIRROR', 'REDIRECT', 'DIRECT_RESPONSE', 'EGRESS')),
    CONSTRAINT ck_route_rules_direction CHECK (traffic_direction IN ('NORTH_SOUTH', 'EAST_WEST', 'EGRESS')),
    CONSTRAINT ck_route_rules_protocol CHECK (protocol IN ('HTTP', 'HTTP2', 'GRPC', 'TCP', 'TLS')),
    CONSTRAINT ck_route_rules_gateways_array CHECK (jsonb_typeof(gateways) = 'array'),
    CONSTRAINT ck_route_rules_hosts_array CHECK (jsonb_typeof(hosts) = 'array'),
    CONSTRAINT ck_route_rules_source_object CHECK (jsonb_typeof(source_selector) = 'object'),
    CONSTRAINT ck_route_rules_matches_array CHECK (jsonb_typeof(match_conditions) = 'array'),
    CONSTRAINT ck_route_rules_destinations_array CHECK (jsonb_typeof(destinations) = 'array')
);

COMMENT ON TABLE route_rules IS 'Typed route rule details aligned with Istio VirtualService and DestinationRule concepts.';

CREATE TABLE breaker_rules (
    governance_rule_id UUID PRIMARY KEY REFERENCES governance_rules (id) ON DELETE CASCADE,
    breaker_type VARCHAR(48) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    target_selector JSONB NOT NULL DEFAULT '{}'::JSONB,
    window_type VARCHAR(32),
    window_size BIGINT,
    minimum_calls BIGINT,
    failure_rate_threshold NUMERIC(7, 4),
    slow_call_rate_threshold NUMERIC(7, 4),
    slow_call_duration_millis BIGINT,
    open_state_wait_millis BIGINT,
    permitted_half_open_calls BIGINT,
    connection_pool_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    outlier_detection_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    retry_budget_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    exception_record_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    exception_ignore_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    fallback_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_breaker_rules_type CHECK (breaker_type IN ('CONNECTION_POOL', 'CONSECUTIVE_ERROR', 'ERROR_RATE', 'SLOW_CALL_RATE', 'EXCEPTION_CLASSIFICATION', 'OUTLIER_DETECTION', 'HALF_OPEN_PROBE', 'RETRY_BUDGET', 'BULKHEAD')),
    CONSTRAINT ck_breaker_rules_protocol CHECK (protocol IN ('HTTP', 'HTTP2', 'GRPC', 'TCP')),
    CONSTRAINT ck_breaker_rules_window_type CHECK (window_type IS NULL OR window_type IN ('COUNT_BASED', 'TIME_BASED')),
    CONSTRAINT ck_breaker_rules_window_size CHECK (window_size IS NULL OR window_size > 0),
    CONSTRAINT ck_breaker_rules_minimum_calls CHECK (minimum_calls IS NULL OR minimum_calls > 0),
    CONSTRAINT ck_breaker_rules_failure_rate CHECK (failure_rate_threshold IS NULL OR (failure_rate_threshold >= 0 AND failure_rate_threshold <= 100)),
    CONSTRAINT ck_breaker_rules_slow_rate CHECK (slow_call_rate_threshold IS NULL OR (slow_call_rate_threshold >= 0 AND slow_call_rate_threshold <= 100)),
    CONSTRAINT ck_breaker_rules_slow_duration CHECK (slow_call_duration_millis IS NULL OR slow_call_duration_millis > 0),
    CONSTRAINT ck_breaker_rules_open_wait CHECK (open_state_wait_millis IS NULL OR open_state_wait_millis > 0),
    CONSTRAINT ck_breaker_rules_half_open CHECK (permitted_half_open_calls IS NULL OR permitted_half_open_calls > 0),
    CONSTRAINT ck_breaker_rules_target_object CHECK (jsonb_typeof(target_selector) = 'object')
);

COMMENT ON TABLE breaker_rules IS 'Typed circuit breaker rule details for connection, error, slow-call, retry and outlier policies.';

CREATE TABLE rate_limit_rules (
    governance_rule_id UUID PRIMARY KEY REFERENCES governance_rules (id) ON DELETE CASCADE,
    limit_type VARCHAR(48) NOT NULL,
    limit_algorithm VARCHAR(48) NOT NULL,
    enforcement_mode VARCHAR(48) NOT NULL DEFAULT 'LOCAL',
    target_selector JSONB NOT NULL DEFAULT '{}'::JSONB,
    dimensions JSONB NOT NULL DEFAULT '[]'::JSONB,
    quota_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    window_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    burst_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    model_limit_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    fallback_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    response_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rate_limit_rules_type CHECK (limit_type IN ('REQUEST', 'CONNECTION', 'BYTE', 'TENANT', 'USER', 'CALLER', 'API_KEY', 'RESOURCE', 'MODEL_REQUEST', 'MODEL_TOKEN', 'MODEL_COST', 'MODEL_CONCURRENCY')),
    CONSTRAINT ck_rate_limit_rules_algorithm CHECK (limit_algorithm IN ('TOKEN_BUCKET', 'LEAKY_BUCKET', 'FIXED_WINDOW', 'SLIDING_WINDOW', 'QUOTA_LEASE')),
    CONSTRAINT ck_rate_limit_rules_mode CHECK (enforcement_mode IN ('LOCAL', 'GLOBAL_SYNC', 'GLOBAL_QUOTA', 'EDGE')),
    CONSTRAINT ck_rate_limit_rules_target_object CHECK (jsonb_typeof(target_selector) = 'object'),
    CONSTRAINT ck_rate_limit_rules_dimensions_array CHECK (jsonb_typeof(dimensions) = 'array'),
    CONSTRAINT ck_rate_limit_rules_quota_object CHECK (jsonb_typeof(quota_config) = 'object'),
    CONSTRAINT ck_rate_limit_rules_window_object CHECK (jsonb_typeof(window_config) = 'object'),
    CONSTRAINT ck_rate_limit_rules_model_object CHECK (jsonb_typeof(model_limit_config) = 'object')
);

COMMENT ON TABLE rate_limit_rules IS 'Typed rate limit rules including local, global, quota lease and model application limits.';

CREATE TABLE rate_limit_quota_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_limit_rule_id UUID NOT NULL REFERENCES rate_limit_rules (governance_rule_id) ON DELETE CASCADE,
    allocation_algorithm VARCHAR(48) NOT NULL,
    assignment_ttl_millis BIGINT NOT NULL,
    report_interval_millis BIGINT NOT NULL,
    rebalance_interval_millis BIGINT NOT NULL,
    max_overdraft_ratio NUMERIC(7, 4) NOT NULL DEFAULT 0,
    hotspot_shard_count INTEGER NOT NULL DEFAULT 1,
    min_assignment_quota BIGINT NOT NULL DEFAULT 0,
    max_assignment_quota BIGINT,
    failover_strategy VARCHAR(48) NOT NULL DEFAULT 'LAST_ASSIGNMENT',
    algorithm_config JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rate_limit_quota_policies_rule UNIQUE (rate_limit_rule_id),
    CONSTRAINT ck_rate_limit_quota_policies_algorithm CHECK (allocation_algorithm IN ('EQUAL_SPLIT', 'WEIGHTED_SPLIT', 'DEMAND_AWARE', 'HOTSPOT_AWARE', 'BORROWING', 'MANUAL')),
    CONSTRAINT ck_rate_limit_quota_policies_ttl CHECK (assignment_ttl_millis > 0),
    CONSTRAINT ck_rate_limit_quota_policies_report CHECK (report_interval_millis > 0),
    CONSTRAINT ck_rate_limit_quota_policies_rebalance CHECK (rebalance_interval_millis > 0),
    CONSTRAINT ck_rate_limit_quota_policies_overdraft CHECK (max_overdraft_ratio >= 0),
    CONSTRAINT ck_rate_limit_quota_policies_shards CHECK (hotspot_shard_count > 0),
    CONSTRAINT ck_rate_limit_quota_policies_failover CHECK (failover_strategy IN ('FAIL_OPEN', 'FAIL_CLOSED', 'LOCAL_FALLBACK', 'LAST_ASSIGNMENT')),
    CONSTRAINT ck_rate_limit_quota_policies_config_object CHECK (jsonb_typeof(algorithm_config) = 'object')
);

COMMENT ON TABLE rate_limit_quota_policies IS 'Quota allocation algorithms used by the distributed rate limit server.';

CREATE TABLE auth_policy_rules (
    governance_rule_id UUID PRIMARY KEY REFERENCES governance_rules (id) ON DELETE CASCADE,
    auth_policy_type VARCHAR(48) NOT NULL,
    auth_action VARCHAR(32),
    mtls_mode VARCHAR(32),
    trust_domain VARCHAR(160),
    workload_selector JSONB NOT NULL DEFAULT '{}'::JSONB,
    peer_sources JSONB NOT NULL DEFAULT '[]'::JSONB,
    request_authentications JSONB NOT NULL DEFAULT '[]'::JSONB,
    authorization_from JSONB NOT NULL DEFAULT '[]'::JSONB,
    authorization_to JSONB NOT NULL DEFAULT '[]'::JSONB,
    authorization_when JSONB NOT NULL DEFAULT '[]'::JSONB,
    jwt_rules JSONB NOT NULL DEFAULT '[]'::JSONB,
    ext_authz_provider VARCHAR(160),
    audit_policy JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_auth_policy_rules_type CHECK (auth_policy_type IN ('PEER_AUTHENTICATION', 'REQUEST_AUTHENTICATION', 'AUTHORIZATION_POLICY', 'EXT_AUTHZ')),
    CONSTRAINT ck_auth_policy_rules_action CHECK (auth_action IS NULL OR auth_action IN ('CUSTOM', 'DENY', 'ALLOW', 'AUDIT')),
    CONSTRAINT ck_auth_policy_rules_mtls CHECK (mtls_mode IS NULL OR mtls_mode IN ('STRICT', 'PERMISSIVE', 'DISABLE')),
    CONSTRAINT ck_auth_policy_rules_workload_object CHECK (jsonb_typeof(workload_selector) = 'object'),
    CONSTRAINT ck_auth_policy_rules_sources_array CHECK (jsonb_typeof(peer_sources) = 'array'),
    CONSTRAINT ck_auth_policy_rules_request_array CHECK (jsonb_typeof(request_authentications) = 'array'),
    CONSTRAINT ck_auth_policy_rules_from_array CHECK (jsonb_typeof(authorization_from) = 'array'),
    CONSTRAINT ck_auth_policy_rules_to_array CHECK (jsonb_typeof(authorization_to) = 'array'),
    CONSTRAINT ck_auth_policy_rules_when_array CHECK (jsonb_typeof(authorization_when) = 'array'),
    CONSTRAINT ck_auth_policy_rules_jwt_array CHECK (jsonb_typeof(jwt_rules) = 'array')
);

COMMENT ON TABLE auth_policy_rules IS 'Typed auth rules aligned with Istio PeerAuthentication, RequestAuthentication and AuthorizationPolicy.';

CREATE TABLE mtls_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID REFERENCES applications (id) ON DELETE RESTRICT,
    certificate_code VARCHAR(160) NOT NULL,
    certificate_name VARCHAR(160) NOT NULL,
    certificate_type VARCHAR(48) NOT NULL,
    usage_type VARCHAR(48) NOT NULL,
    trust_domain VARCHAR(160),
    subject_dn TEXT,
    issuer_dn TEXT,
    serial_number VARCHAR(160),
    fingerprint_sha256 VARCHAR(128) NOT NULL,
    certificate_chain_pem TEXT NOT NULL,
    public_certificate_pem TEXT NOT NULL,
    encrypted_private_key BYTEA,
    private_key_algorithm VARCHAR(80),
    encryption_key_id VARCHAR(160),
    not_before TIMESTAMPTZ NOT NULL,
    not_after TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_mtls_certificates_code UNIQUE (instance_space_id, certificate_code),
    CONSTRAINT uk_mtls_certificates_fingerprint UNIQUE (fingerprint_sha256),
    CONSTRAINT ck_mtls_certificates_type CHECK (certificate_type IN ('ROOT_CA', 'INTERMEDIATE_CA', 'WORKLOAD_CERT', 'CLIENT_CERT', 'SERVER_CERT')),
    CONSTRAINT ck_mtls_certificates_usage CHECK (usage_type IN ('MTLS', 'TLS_TERMINATION', 'JWT_SIGNING', 'JWKS')),
    CONSTRAINT ck_mtls_certificates_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'ROTATING', 'ARCHIVED')),
    CONSTRAINT ck_mtls_certificates_validity CHECK (not_after > not_before)
);

COMMENT ON TABLE mtls_certificates IS 'mTLS certificates and encrypted private keys for client runtime delivery.';

CREATE TABLE auth_rule_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_rule_id UUID NOT NULL REFERENCES auth_policy_rules (governance_rule_id) ON DELETE CASCADE,
    certificate_id UUID NOT NULL REFERENCES mtls_certificates (id) ON DELETE RESTRICT,
    binding_type VARCHAR(48) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_auth_rule_certificates UNIQUE (auth_rule_id, certificate_id, binding_type),
    CONSTRAINT ck_auth_rule_certificates_type CHECK (binding_type IN ('TRUST_ANCHOR', 'WORKLOAD_IDENTITY', 'CLIENT_CERT', 'SERVER_CERT', 'JWKS'))
);

COMMENT ON TABLE auth_rule_certificates IS 'Bindings between auth policies and certificates or JWKS material.';

CREATE TABLE rule_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES governance_rules (id) ON DELETE CASCADE,
    draft_version BIGINT NOT NULL,
    source_format VARCHAR(32) NOT NULL DEFAULT 'CUE',
    validation_status VARCHAR(32) NOT NULL,
    normalized_snapshot_json JSONB,
    normalized_snapshot_bytes BYTEA,
    error_messages JSONB NOT NULL DEFAULT '[]'::JSONB,
    warning_messages JSONB NOT NULL DEFAULT '[]'::JSONB,
    validated_by VARCHAR(120) NOT NULL,
    validated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rule_validations_source_format CHECK (source_format = 'CUE'),
    CONSTRAINT ck_rule_validations_status CHECK (validation_status IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_rule_validations_errors_array CHECK (jsonb_typeof(error_messages) = 'array'),
    CONSTRAINT ck_rule_validations_warnings_array CHECK (jsonb_typeof(warning_messages) = 'array')
);

COMMENT ON TABLE rule_validations IS 'CUE validation records before rule publishing.';

CREATE TABLE rule_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    release_version BIGINT NOT NULL,
    release_name VARCHAR(160) NOT NULL,
    release_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    idempotency_key VARCHAR(160),
    source_format VARCHAR(32) NOT NULL DEFAULT 'CUE',
    runtime_format VARCHAR(32) NOT NULL DEFAULT 'JSON',
    release_snapshot_json JSONB,
    release_snapshot_bytes BYTEA,
    checksum VARCHAR(128) NOT NULL,
    rollback_from_release_id UUID REFERENCES rule_releases (id) ON DELETE RESTRICT,
    release_note TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 3,
    failure_details JSONB NOT NULL DEFAULT '[]'::JSONB,
    recovery_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    recovered_by VARCHAR(120),
    recovered_at TIMESTAMPTZ,
    recovery_note TEXT,
    created_by VARCHAR(120) NOT NULL,
    published_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rule_releases_version UNIQUE (instance_space_id, application_id, release_version),
    CONSTRAINT uk_rule_releases_idempotency UNIQUE (instance_space_id, application_id, idempotency_key),
    CONSTRAINT ck_rule_releases_status CHECK (release_status IN ('CREATED', 'VALIDATING', 'PUBLISHING', 'PARTIAL_PUBLISHED', 'PUBLISHED', 'FAILED', 'ROLLED_BACK')),
    CONSTRAINT ck_rule_releases_source_format CHECK (source_format = 'CUE'),
    CONSTRAINT ck_rule_releases_runtime_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_rule_releases_retry CHECK (retry_count >= 0 AND max_retry_count >= 0),
    CONSTRAINT ck_rule_releases_failure_details_array CHECK (jsonb_typeof(failure_details) = 'array'),
    CONSTRAINT ck_rule_releases_recovery_status CHECK (recovery_status IN ('NONE', 'MANUAL_RECOVERED')),
    CONSTRAINT ck_rule_releases_snapshot CHECK (
        (runtime_format = 'JSON' AND release_snapshot_json IS NOT NULL AND release_snapshot_bytes IS NULL)
        OR (runtime_format = 'PROTOBUF' AND release_snapshot_json IS NULL AND release_snapshot_bytes IS NOT NULL)
    )
);

COMMENT ON TABLE rule_releases IS 'Published governance release versions and full runtime snapshots.';

ALTER TABLE governance_rules
    ADD CONSTRAINT fk_governance_rules_latest_release
    FOREIGN KEY (latest_release_id) REFERENCES rule_releases (id) ON DELETE SET NULL;

CREATE TABLE release_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES governance_rules (id) ON DELETE RESTRICT,
    rule_type VARCHAR(48) NOT NULL,
    rule_code VARCHAR(160) NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    draft_version BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    cue_source TEXT NOT NULL,
    runtime_snapshot_json JSONB,
    runtime_snapshot_bytes BYTEA,
    checksum VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_release_items_rule UNIQUE (release_id, rule_id),
    CONSTRAINT ck_release_items_type CHECK (rule_type IN ('ROUTE', 'BREAKER', 'RATE_LIMIT', 'AUTH')),
    CONSTRAINT ck_release_items_snapshot CHECK (runtime_snapshot_json IS NOT NULL OR runtime_snapshot_bytes IS NOT NULL)
);

COMMENT ON TABLE release_items IS 'Rule-level immutable snapshots included in a release.';

CREATE TABLE stellnula_publish_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE CASCADE,
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    publish_kind VARCHAR(48) NOT NULL,
    namespace_code VARCHAR(160) NOT NULL,
    config_group VARCHAR(160) NOT NULL,
    config_key VARCHAR(360) NOT NULL,
    data_id VARCHAR(360) NOT NULL,
    content_type VARCHAR(80) NOT NULL DEFAULT 'application/json',
    runtime_format VARCHAR(32) NOT NULL DEFAULT 'JSON',
    payload_text TEXT,
    payload_bytes BYTEA,
    payload_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    checksum VARCHAR(128) NOT NULL,
    target_version VARCHAR(120),
    publish_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(160),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    failure_details JSONB NOT NULL DEFAULT '[]'::JSONB,
    error_message TEXT,
    recovered_by VARCHAR(120),
    recovered_at TIMESTAMPTZ,
    recovery_note TEXT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_stellnula_publish_records_key UNIQUE (release_id, namespace_code, config_group, data_id),
    CONSTRAINT ck_stellnula_publish_records_kind CHECK (publish_kind IN ('RULE_SNAPSHOT', 'ROUTE_RULES', 'BREAKER_RULES', 'RATE_LIMIT_RULES', 'AUTH_RULES', 'MTLS_CERTIFICATE', 'JWKS', 'RATE_LIMIT_ASSIGNMENT')),
    CONSTRAINT ck_stellnula_publish_records_runtime_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_stellnula_publish_records_status CHECK (publish_status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_stellnula_publish_records_retry CHECK (retry_count >= 0 AND max_retry_count >= 0),
    CONSTRAINT ck_stellnula_publish_records_payload_object CHECK (jsonb_typeof(payload_metadata) = 'object'),
    CONSTRAINT ck_stellnula_publish_records_failure_array CHECK (jsonb_typeof(failure_details) = 'array'),
    CONSTRAINT ck_stellnula_publish_records_payload CHECK (
        (runtime_format = 'JSON' AND payload_text IS NOT NULL AND payload_bytes IS NULL)
        OR (runtime_format = 'PROTOBUF' AND payload_text IS NULL AND payload_bytes IS NOT NULL)
    )
);

COMMENT ON TABLE stellnula_publish_records IS 'Records of rule snapshots, mTLS certificates and JWKS published to stellnula-service.';

CREATE TABLE client_runtime_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    client_id VARCHAR(160) NOT NULL,
    client_version VARCHAR(80) NOT NULL,
    protocol_version VARCHAR(64) NOT NULL,
    snapshot_schema_version VARCHAR(64) NOT NULL,
    runtime_format VARCHAR(32) NOT NULL,
    current_release_id UUID REFERENCES rule_releases (id) ON DELETE SET NULL,
    current_release_version BIGINT,
    client_address VARCHAR(240),
    zone VARCHAR(120),
    labels JSONB NOT NULL DEFAULT '{}'::JSONB,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    rate_limit_ring_version VARCHAR(64),
    session_status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_client_runtime_sessions_client UNIQUE (instance_space_id, application_id, client_id),
    CONSTRAINT ck_client_runtime_sessions_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_client_runtime_sessions_status CHECK (session_status IN ('ONLINE', 'STALE', 'OFFLINE')),
    CONSTRAINT ck_client_runtime_sessions_labels_object CHECK (jsonb_typeof(labels) = 'object'),
    CONSTRAINT ck_client_runtime_sessions_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

COMMENT ON TABLE client_runtime_sessions IS 'Runtime client sessions used for version negotiation, heartbeat and instance view.';

CREATE TABLE client_runtime_acks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE CASCADE,
    release_version BIGINT NOT NULL,
    client_id VARCHAR(160) NOT NULL,
    client_version VARCHAR(80) NOT NULL,
    protocol_version VARCHAR(64) NOT NULL,
    snapshot_schema_version VARCHAR(64) NOT NULL,
    runtime_format VARCHAR(32) NOT NULL,
    ack_status VARCHAR(32) NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    message TEXT,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_client_runtime_acks_release_client UNIQUE (release_id, client_id),
    CONSTRAINT ck_client_runtime_acks_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_client_runtime_acks_status CHECK (ack_status IN ('RECEIVED', 'APPLIED', 'REJECTED', 'ROLLBACK_APPLIED', 'STALE'))
);

COMMENT ON TABLE client_runtime_acks IS 'Client ACK records for delivered runtime rule snapshots.';

CREATE TABLE client_runtime_status_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    release_id UUID REFERENCES rule_releases (id) ON DELETE SET NULL,
    release_version BIGINT,
    client_id VARCHAR(160) NOT NULL,
    client_version VARCHAR(80) NOT NULL,
    protocol_version VARCHAR(64) NOT NULL,
    snapshot_schema_version VARCHAR(64) NOT NULL,
    runtime_format VARCHAR(32) NOT NULL,
    effective_status VARCHAR(32) NOT NULL,
    rule_statuses JSONB NOT NULL DEFAULT '[]'::JSONB,
    error_details JSONB NOT NULL DEFAULT '[]'::JSONB,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_client_runtime_status_reports_format CHECK (runtime_format IN ('JSON', 'PROTOBUF')),
    CONSTRAINT ck_client_runtime_status_reports_status CHECK (effective_status IN ('APPLIED', 'PARTIAL_APPLIED', 'FAILED', 'ROLLING_BACK', 'ROLLED_BACK')),
    CONSTRAINT ck_client_runtime_status_reports_rules_array CHECK (jsonb_typeof(rule_statuses) = 'array'),
    CONSTRAINT ck_client_runtime_status_reports_errors_array CHECK (jsonb_typeof(error_details) = 'array')
);

COMMENT ON TABLE client_runtime_status_reports IS 'Client effective-state reports for runtime rule snapshots.';

CREATE TABLE publish_locks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE CASCADE,
    lock_key VARCHAR(220) NOT NULL,
    lock_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    locked_by VARCHAR(120) NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_publish_locks_status CHECK (lock_status IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_publish_locks_expiry CHECK (expires_at > locked_at)
);

COMMENT ON TABLE publish_locks IS 'Application-scoped publish locks preventing concurrent releases for the same governance target.';

CREATE UNIQUE INDEX uk_publish_locks_active_key ON publish_locks (lock_key) WHERE lock_status = 'ACTIVE';

CREATE TABLE publish_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE CASCADE,
    publish_record_id UUID REFERENCES stellnula_publish_records (id) ON DELETE CASCADE,
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE CASCADE,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    job_type VARCHAR(48) NOT NULL,
    job_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(220) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_by VARCHAR(120),
    locked_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    failure_details JSONB NOT NULL DEFAULT '[]'::JSONB,
    payload_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_publish_jobs_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_publish_jobs_type CHECK (job_type IN ('PUBLISH_RECORD', 'RECONCILE_RECORD', 'COMPENSATE_RELEASE')),
    CONSTRAINT ck_publish_jobs_status CHECK (job_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    CONSTRAINT ck_publish_jobs_attempts CHECK (attempt_count >= 0 AND max_attempts >= 0),
    CONSTRAINT ck_publish_jobs_failure_array CHECK (jsonb_typeof(failure_details) = 'array'),
    CONSTRAINT ck_publish_jobs_payload_object CHECK (jsonb_typeof(payload_metadata) = 'object')
);

COMMENT ON TABLE publish_jobs IS 'Outbox jobs used by publish workers to asynchronously publish, reconcile and compensate Stellnula records.';

CREATE TABLE rate_limit_quota_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_limit_rule_id UUID NOT NULL REFERENCES rate_limit_rules (governance_rule_id) ON DELETE CASCADE,
    quota_policy_id UUID NOT NULL REFERENCES rate_limit_quota_policies (id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE RESTRICT,
    client_id VARCHAR(160) NOT NULL,
    limit_key_hash VARCHAR(128) NOT NULL,
    assigned_quota BIGINT NOT NULL,
    used_quota BIGINT NOT NULL DEFAULT 0,
    remaining_quota BIGINT NOT NULL,
    lease_version BIGINT NOT NULL,
    lease_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rate_limit_quota_assignments UNIQUE (rate_limit_rule_id, client_id, limit_key_hash, lease_version),
    CONSTRAINT ck_rate_limit_quota_assignments_quota CHECK (assigned_quota >= 0),
    CONSTRAINT ck_rate_limit_quota_assignments_used CHECK (used_quota >= 0),
    CONSTRAINT ck_rate_limit_quota_assignments_remaining CHECK (remaining_quota >= 0),
    CONSTRAINT ck_rate_limit_quota_assignments_status CHECK (lease_status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'REBALANCED')),
    CONSTRAINT ck_rate_limit_quota_assignments_expiry CHECK (expires_at > assigned_at)
);

COMMENT ON TABLE rate_limit_quota_assignments IS 'Quota lease assignments issued to distributed rate limit clients.';

CREATE TABLE rate_limit_usage_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES rate_limit_quota_assignments (id) ON DELETE SET NULL,
    rate_limit_rule_id UUID NOT NULL REFERENCES rate_limit_rules (governance_rule_id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE RESTRICT,
    client_id VARCHAR(160) NOT NULL,
    limit_key_hash VARCHAR(128) NOT NULL,
    reported_used BIGINT NOT NULL,
    reported_allowed BIGINT NOT NULL DEFAULT 0,
    reported_rejected BIGINT NOT NULL DEFAULT 0,
    model_usage JSONB NOT NULL DEFAULT '{}'::JSONB,
    report_window_start TIMESTAMPTZ NOT NULL,
    report_window_end TIMESTAMPTZ NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rate_limit_usage_reports_used CHECK (reported_used >= 0),
    CONSTRAINT ck_rate_limit_usage_reports_allowed CHECK (reported_allowed >= 0),
    CONSTRAINT ck_rate_limit_usage_reports_rejected CHECK (reported_rejected >= 0),
    CONSTRAINT ck_rate_limit_usage_reports_model_object CHECK (jsonb_typeof(model_usage) = 'object'),
    CONSTRAINT ck_rate_limit_usage_reports_window CHECK (report_window_end > report_window_start)
);

COMMENT ON TABLE rate_limit_usage_reports IS 'Periodic usage reports from clients for quota rebalance and model usage accounting.';

CREATE TABLE rate_limit_buckets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    rate_limit_rule_id UUID NOT NULL REFERENCES rate_limit_rules (governance_rule_id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE RESTRICT,
    limit_key_hash VARCHAR(128) NOT NULL,
    limit_key TEXT NOT NULL,
    window_strategy VARCHAR(48) NOT NULL,
    window_start_at TIMESTAMPTZ NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    quota BIGINT NOT NULL,
    used_permits BIGINT NOT NULL DEFAULT 0,
    remaining_permits BIGINT NOT NULL,
    reset_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rate_limit_buckets_window UNIQUE (rate_limit_rule_id, release_id, limit_key_hash, window_start_at),
    CONSTRAINT ck_rate_limit_buckets_strategy CHECK (window_strategy IN ('TOKEN_BUCKET', 'LEAKY_BUCKET', 'FIXED_WINDOW', 'SLIDING_WINDOW', 'QUOTA_LEASE')),
    CONSTRAINT ck_rate_limit_buckets_quota CHECK (quota >= 0),
    CONSTRAINT ck_rate_limit_buckets_used CHECK (used_permits >= 0),
    CONSTRAINT ck_rate_limit_buckets_remaining CHECK (remaining_permits >= 0),
    CONSTRAINT ck_rate_limit_buckets_window_time CHECK (window_end_at > window_start_at),
    CONSTRAINT ck_rate_limit_buckets_remaining_capacity CHECK (remaining_permits <= quota)
);

COMMENT ON TABLE rate_limit_buckets IS 'Server-side distributed rate limit bucket states for synchronous decisions.';

CREATE TABLE rate_limit_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_id UUID REFERENCES rate_limit_buckets (id) ON DELETE SET NULL,
    assignment_id UUID REFERENCES rate_limit_quota_assignments (id) ON DELETE SET NULL,
    instance_space_id UUID NOT NULL REFERENCES instance_spaces (id) ON DELETE RESTRICT,
    application_id UUID NOT NULL REFERENCES applications (id) ON DELETE RESTRICT,
    rate_limit_rule_id UUID NOT NULL REFERENCES rate_limit_rules (governance_rule_id) ON DELETE CASCADE,
    release_id UUID NOT NULL REFERENCES rule_releases (id) ON DELETE RESTRICT,
    request_id VARCHAR(160),
    client_id VARCHAR(160),
    limit_key_hash VARCHAR(128) NOT NULL,
    requested_permits BIGINT NOT NULL DEFAULT 1,
    model_request_units JSONB NOT NULL DEFAULT '{}'::JSONB,
    allowed BOOLEAN NOT NULL,
    remaining_permits BIGINT,
    retry_after_millis BIGINT,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    decision_reason VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_rate_limit_decisions_permits CHECK (requested_permits > 0),
    CONSTRAINT ck_rate_limit_decisions_remaining CHECK (remaining_permits IS NULL OR remaining_permits >= 0),
    CONSTRAINT ck_rate_limit_decisions_retry_after CHECK (retry_after_millis IS NULL OR retry_after_millis >= 0),
    CONSTRAINT ck_rate_limit_decisions_model_object CHECK (jsonb_typeof(model_request_units) = 'object')
);

COMMENT ON TABLE rate_limit_decisions IS 'Append-only distributed rate limit decision logs.';

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id UUID,
    tenant_id VARCHAR(120) NOT NULL,
    instance_space_id UUID REFERENCES instance_spaces (id) ON DELETE SET NULL,
    application_id UUID REFERENCES applications (id) ON DELETE SET NULL,
    operator VARCHAR(120) NOT NULL,
    operator_ip INET,
    user_agent TEXT,
    event_detail JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_audit_events_detail_object CHECK (jsonb_typeof(event_detail) = 'object')
);

COMMENT ON TABLE audit_events IS 'Audit trail for rule CRUD, validation, publication, rollback and runtime operations.';

CREATE TRIGGER trg_instance_spaces_touch_updated_at
BEFORE UPDATE ON instance_spaces
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_applications_touch_updated_at
BEFORE UPDATE ON applications
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_governance_rules_touch_updated_at
BEFORE UPDATE ON governance_rules
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_cue_schema_versions_touch_updated_at
BEFORE UPDATE ON cue_schema_versions
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_route_rules_touch_updated_at
BEFORE UPDATE ON route_rules
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_breaker_rules_touch_updated_at
BEFORE UPDATE ON breaker_rules
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_rate_limit_rules_touch_updated_at
BEFORE UPDATE ON rate_limit_rules
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_rate_limit_quota_policies_touch_updated_at
BEFORE UPDATE ON rate_limit_quota_policies
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_auth_policy_rules_touch_updated_at
BEFORE UPDATE ON auth_policy_rules
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_mtls_certificates_touch_updated_at
BEFORE UPDATE ON mtls_certificates
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_rule_releases_touch_updated_at
BEFORE UPDATE ON rule_releases
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_stellnula_publish_records_touch_updated_at
BEFORE UPDATE ON stellnula_publish_records
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_client_runtime_sessions_touch_updated_at
BEFORE UPDATE ON client_runtime_sessions
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_publish_locks_touch_updated_at
BEFORE UPDATE ON publish_locks
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_publish_jobs_touch_updated_at
BEFORE UPDATE ON publish_jobs
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_rate_limit_quota_assignments_touch_updated_at
BEFORE UPDATE ON rate_limit_quota_assignments
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_rate_limit_buckets_touch_updated_at
BEFORE UPDATE ON rate_limit_buckets
FOR EACH ROW
EXECUTE FUNCTION touch_updated_at();

CREATE INDEX idx_instance_spaces_status ON instance_spaces (status);
CREATE INDEX idx_instance_spaces_tenant_status ON instance_spaces (tenant_id, status);
CREATE INDEX idx_instance_spaces_labels_gin ON instance_spaces USING GIN (labels);
CREATE INDEX idx_applications_space_status ON applications (instance_space_id, status);
CREATE INDEX idx_applications_labels_gin ON applications USING GIN (labels);

CREATE INDEX idx_governance_rules_space_app_type_status ON governance_rules (instance_space_id, application_id, rule_type, status);
CREATE INDEX idx_governance_rules_latest_release ON governance_rules (latest_release_id);
CREATE INDEX idx_governance_rules_tags_gin ON governance_rules USING GIN (tags);
CREATE INDEX idx_governance_rules_runtime_snapshot_json_gin ON governance_rules USING GIN (runtime_snapshot_json);
CREATE INDEX idx_cue_schema_versions_type_status ON cue_schema_versions (rule_type, status);

CREATE INDEX idx_route_rules_type_protocol ON route_rules (route_type, protocol);
CREATE INDEX idx_route_rules_direction ON route_rules (traffic_direction);
CREATE INDEX idx_route_rules_hosts_gin ON route_rules USING GIN (hosts);
CREATE INDEX idx_route_rules_matches_gin ON route_rules USING GIN (match_conditions);
CREATE INDEX idx_route_rules_destinations_gin ON route_rules USING GIN (destinations);

CREATE INDEX idx_breaker_rules_type_protocol ON breaker_rules (breaker_type, protocol);
CREATE INDEX idx_breaker_rules_target_gin ON breaker_rules USING GIN (target_selector);

CREATE INDEX idx_rate_limit_rules_type_mode ON rate_limit_rules (limit_type, enforcement_mode);
CREATE INDEX idx_rate_limit_rules_dimensions_gin ON rate_limit_rules USING GIN (dimensions);
CREATE INDEX idx_rate_limit_rules_model_gin ON rate_limit_rules USING GIN (model_limit_config);
CREATE INDEX idx_rate_limit_quota_policies_algorithm ON rate_limit_quota_policies (allocation_algorithm);

CREATE INDEX idx_auth_policy_rules_type_action ON auth_policy_rules (auth_policy_type, auth_action);
CREATE INDEX idx_auth_policy_rules_jwt_gin ON auth_policy_rules USING GIN (jwt_rules);
CREATE INDEX idx_auth_policy_rules_from_gin ON auth_policy_rules USING GIN (authorization_from);
CREATE INDEX idx_auth_policy_rules_to_gin ON auth_policy_rules USING GIN (authorization_to);
CREATE INDEX idx_auth_policy_rules_when_gin ON auth_policy_rules USING GIN (authorization_when);
CREATE INDEX idx_mtls_certificates_space_status ON mtls_certificates (instance_space_id, status);
CREATE INDEX idx_mtls_certificates_validity ON mtls_certificates (not_before, not_after);

CREATE INDEX idx_rule_validations_rule_version ON rule_validations (rule_id, draft_version);
CREATE INDEX idx_rule_releases_space_app_status ON rule_releases (instance_space_id, application_id, release_status);
CREATE INDEX idx_rule_releases_snapshot_json_gin ON rule_releases USING GIN (release_snapshot_json);
CREATE INDEX idx_release_items_release_type ON release_items (release_id, rule_type);
CREATE INDEX idx_stellnula_publish_records_release_status ON stellnula_publish_records (release_id, publish_status);
CREATE INDEX idx_stellnula_publish_records_kind ON stellnula_publish_records (publish_kind);
CREATE INDEX idx_client_runtime_sessions_space_app_status ON client_runtime_sessions (instance_space_id, application_id, session_status);
CREATE INDEX idx_client_runtime_sessions_heartbeat ON client_runtime_sessions (last_heartbeat_at);
CREATE INDEX idx_client_runtime_sessions_labels_gin ON client_runtime_sessions USING GIN (labels);
CREATE INDEX idx_client_runtime_acks_release_status ON client_runtime_acks (release_id, ack_status);
CREATE INDEX idx_client_runtime_acks_client_created ON client_runtime_acks (client_id, created_at);
CREATE INDEX idx_client_runtime_status_reports_release_status ON client_runtime_status_reports (release_id, effective_status);
CREATE INDEX idx_client_runtime_status_reports_client_created ON client_runtime_status_reports (client_id, created_at);
CREATE INDEX idx_client_runtime_status_reports_rules_gin ON client_runtime_status_reports USING GIN (rule_statuses);
CREATE INDEX idx_publish_locks_key_status ON publish_locks (lock_key, lock_status);
CREATE INDEX idx_publish_locks_expires_at ON publish_locks (expires_at);
CREATE INDEX idx_publish_jobs_status_next_run ON publish_jobs (job_status, next_run_at);
CREATE INDEX idx_publish_jobs_release ON publish_jobs (release_id);
CREATE INDEX idx_publish_jobs_record ON publish_jobs (publish_record_id);

CREATE INDEX idx_rate_limit_quota_assignments_rule_client ON rate_limit_quota_assignments (rate_limit_rule_id, client_id);
CREATE INDEX idx_rate_limit_quota_assignments_expires_at ON rate_limit_quota_assignments (expires_at);
CREATE INDEX idx_rate_limit_usage_reports_rule_window ON rate_limit_usage_reports (rate_limit_rule_id, report_window_start, report_window_end);
CREATE INDEX idx_rate_limit_usage_reports_client ON rate_limit_usage_reports (client_id, reported_at);
CREATE INDEX idx_rate_limit_buckets_expires_at ON rate_limit_buckets (expires_at);
CREATE INDEX idx_rate_limit_buckets_rule_key ON rate_limit_buckets (rate_limit_rule_id, limit_key_hash);
CREATE INDEX idx_rate_limit_buckets_window ON rate_limit_buckets (window_start_at, window_end_at);
CREATE INDEX idx_rate_limit_decisions_rule_created ON rate_limit_decisions (rate_limit_rule_id, created_at);
CREATE INDEX idx_rate_limit_decisions_key_created ON rate_limit_decisions (limit_key_hash, created_at);
CREATE INDEX idx_rate_limit_decisions_model_gin ON rate_limit_decisions USING GIN (model_request_units);

CREATE INDEX idx_audit_events_resource ON audit_events (resource_type, resource_id);
CREATE INDEX idx_audit_events_tenant_created_at ON audit_events (tenant_id, created_at);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);

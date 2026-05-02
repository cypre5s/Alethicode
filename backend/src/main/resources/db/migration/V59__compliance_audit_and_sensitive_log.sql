-- V59: Compliance scaffolding required for operating Alethicode in Mainland China.
--
-- Regulations explicitly addressed:
--   * 《生成式人工智能服务管理暂行办法》(AIGC Interim Measures, 国家网信办 2023 起草)
--     Article 12 — providers must label AI-generated content.
--     Article 10/11 — providers must log input/output and user records for at least 6 months.
--   * 《个人信息保护法》(PIPL, 2021-11)
--     Article 44-47 — data subject rights (access, copy, correct, delete, portability).
--     Article 55 — impact assessments and audit logs for sensitive-data processing.
--   * 《数据安全法》(DSL, 2021-09) Article 27 — organizations must record data access
--     and preserve logs for audit.
--
-- This migration adds the minimum schema to support those obligations. Retention
-- and deletion policies are enforced at the application level (AigcComplianceService
-- and PiplDataSubjectService) rather than DB triggers, so the policies are
-- reviewable and testable.

-- 1. AIGC audit trail: every AI-generated response and its stimulus must be
--    persisted for 6 months. `content_tagged` records whether the service actually
--    returned the response with an "AI-generated" disclaimer to the end user.
CREATE TABLE IF NOT EXISTS aigc_audit_log (
    id                BIGSERIAL       PRIMARY KEY,
    user_id           BIGINT,
    session_id        VARCHAR(64),
    run_id            VARCHAR(128),
    surface           VARCHAR(64)     NOT NULL, -- 'tutor_workflow' | 'language_pack_qa' | 'chat' | ...
    model_family      VARCHAR(64),              -- 'minimax' | 'qwen' | 'openai' | ...
    input_hash        VARCHAR(128)    NOT NULL,
    output_hash       VARCHAR(128)    NOT NULL,
    input_preview     TEXT            NOT NULL DEFAULT '',
    output_preview    TEXT            NOT NULL DEFAULT '',
    content_tagged    BOOLEAN         NOT NULL DEFAULT TRUE,
    sensitive_flags   JSONB           NOT NULL DEFAULT '[]'::JSONB,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- six-month retention is the regulatory floor; the application sweeps older rows.
    retention_expires_at TIMESTAMPTZ  NOT NULL DEFAULT (NOW() + INTERVAL '6 months')
);

CREATE INDEX IF NOT EXISTS idx_aigc_audit_user_time
    ON aigc_audit_log (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_aigc_audit_retention
    ON aigc_audit_log (retention_expires_at);

-- 2. Personal-data access audit: every staff / admin / automated access to PII
--    must be recorded. Rows in this table are append-only; the application must
--    never UPDATE/DELETE except through a retention job (5 years for PIPL auditability).
CREATE TABLE IF NOT EXISTS pii_access_log (
    id              BIGSERIAL       PRIMARY KEY,
    data_subject_id BIGINT          NOT NULL,
    accessor_id     BIGINT,          -- NULL = system / batch job
    accessor_role   VARCHAR(32)     NOT NULL, -- 'self' | 'teacher' | 'admin' | 'system'
    action          VARCHAR(32)     NOT NULL, -- 'export' | 'delete' | 'read' | 'update'
    payload_summary JSONB           NOT NULL DEFAULT '{}'::JSONB,
    client_ip       VARCHAR(64),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pii_access_subject_time
    ON pii_access_log (data_subject_id, created_at DESC);

-- 3. PIPL deletion request intake. Requests are tracked from submission through
--    completion so there is a verifiable record in case of regulator audit.
CREATE TABLE IF NOT EXISTS pii_deletion_request (
    id              BIGSERIAL       PRIMARY KEY,
    data_subject_id BIGINT          NOT NULL,
    requested_by_id BIGINT,          -- may equal data_subject_id, or an admin acting on behalf
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',  -- PENDING / IN_PROGRESS / COMPLETED / REJECTED
    reason          TEXT            NOT NULL DEFAULT '',
    requested_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    resolution_note TEXT            NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_pii_deletion_subject_status
    ON pii_deletion_request (data_subject_id, status);

-- V92__twin_portable_phase_d.sql
-- L99 Phase D: 永久主页 + 学期报告 + 学习证书 + 孪生导出

CREATE TABLE IF NOT EXISTS twin_public_profile (
    user_id            BIGINT       PRIMARY KEY,
    public_handle      VARCHAR(64)  NOT NULL UNIQUE,
    privacy_level      VARCHAR(16)  NOT NULL DEFAULT 'private',
    show_kc_galaxy     BOOLEAN      NOT NULL DEFAULT TRUE,
    show_timeline      BOOLEAN      NOT NULL DEFAULT FALSE,
    show_museum        BOOLEAN      NOT NULL DEFAULT TRUE,
    show_persona       BOOLEAN      NOT NULL DEFAULT TRUE,
    show_insights      BOOLEAN      NOT NULL DEFAULT TRUE,
    custom_bio         VARCHAR(500),
    avatar_url         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tpp_handle
  ON twin_public_profile(public_handle);

CREATE TABLE IF NOT EXISTS semester_report (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    classroom_id    BIGINT,
    semester_label  VARCHAR(64)  NOT NULL,
    theme_skin      VARCHAR(32)  NOT NULL DEFAULT 'default',
    summary_text    TEXT         NOT NULL,
    metrics         JSONB        NOT NULL,
    pdf_storage_path VARCHAR(500),
    email_sent_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sr_user
  ON semester_report(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS verifiable_credential (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    credential_id   VARCHAR(128) NOT NULL UNIQUE,
    credential_type VARCHAR(64)  NOT NULL,
    issuer_did      VARCHAR(255) NOT NULL,
    subject_did     VARCHAR(255) NOT NULL,
    payload_jsonld  JSONB        NOT NULL,
    proof_jws       TEXT         NOT NULL,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_vc_user
  ON verifiable_credential(user_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_vc_subject
  ON verifiable_credential(subject_did);

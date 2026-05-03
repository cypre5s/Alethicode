-- V91__twin_kc_decay_and_arena.sql
-- L99 Sprint 14 + 15: KC 遗忘状态 + AI 对决竞技场

CREATE TABLE IF NOT EXISTS twin_kc_fsrs_state (
    user_id             BIGINT       NOT NULL,
    kc_id               BIGINT       NOT NULL,
    fsrs_stability      NUMERIC(10,4) NOT NULL DEFAULT 0.7,
    fsrs_difficulty     NUMERIC(10,4) NOT NULL DEFAULT 5.0,
    fsrs_reps           INTEGER      NOT NULL DEFAULT 0,
    fsrs_lapses         INTEGER      NOT NULL DEFAULT 0,
    fsrs_last_review_at TIMESTAMPTZ,
    fsrs_due_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decay_state         VARCHAR(16)  NOT NULL DEFAULT 'fresh',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, kc_id)
);

CREATE INDEX IF NOT EXISTS idx_tkfs_user_due
  ON twin_kc_fsrs_state(user_id, fsrs_due_at);
CREATE INDEX IF NOT EXISTS idx_tkfs_user_state
  ON twin_kc_fsrs_state(user_id, decay_state);

CREATE TABLE IF NOT EXISTS ai_arena_match (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    problem_id            BIGINT       NOT NULL,
    student_code          TEXT,
    ai_code               TEXT         NOT NULL,
    ai_difficulty_level   VARCHAR(16)  NOT NULL,
    ai_judge_result       INTEGER,
    student_judge_result  INTEGER,
    student_evaluation    TEXT,
    student_score_for_ai  INTEGER,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_aam_user_problem
  ON ai_arena_match(user_id, problem_id);

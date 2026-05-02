CREATE TABLE IF NOT EXISTS classroom (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    course_code VARCHAR(64),
    semester VARCHAR(32),
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    allow_student_view_others BOOLEAN NOT NULL DEFAULT FALSE,
    enable_ai_tutor BOOLEAN NOT NULL DEFAULT TRUE,
    enable_collaboration BOOLEAN NOT NULL DEFAULT TRUE,
    current_chapter INTEGER NOT NULL DEFAULT 1,
    chapter_unlock_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    member_count INTEGER NOT NULL DEFAULT 0,
    problem_count INTEGER NOT NULL DEFAULT 0,
    lesson_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_creator_active
    ON classroom(created_by_id, is_active);

CREATE TABLE IF NOT EXISTS classroom_member (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'student',
    join_method VARCHAR(20) NOT NULL DEFAULT 'invited',
    nickname VARCHAR(128),
    student_id VARCHAR(64),
    problems_solved INTEGER NOT NULL DEFAULT 0,
    last_active_time TIMESTAMPTZ,
    join_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (classroom_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_member_classroom_role
    ON classroom_member(classroom_id, role);

CREATE TABLE IF NOT EXISTS classroom_invitation (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    code_hash VARCHAR(128) NOT NULL UNIQUE,
    code_plain VARCHAR(32) NOT NULL UNIQUE,
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    max_uses INTEGER NOT NULL DEFAULT 0,
    current_uses INTEGER NOT NULL DEFAULT 0,
    expire_time TIMESTAMPTZ,
    default_role VARCHAR(20) NOT NULL DEFAULT 'student',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_time TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_classroom_invitation_classroom_active
    ON classroom_invitation(classroom_id, is_active);

CREATE TABLE IF NOT EXISTS classroom_lesson (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    lesson_type VARCHAR(20) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    total_pages INTEGER NOT NULL DEFAULT 0,
    table_of_contents JSONB NOT NULL DEFAULT '[]'::jsonb,
    vision_analysis JSONB NOT NULL DEFAULT '{}'::jsonb,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_by_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_lesson_classroom_order
    ON classroom_lesson(classroom_id, display_order, create_time DESC);

CREATE TABLE IF NOT EXISTS classroom_problem (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    problem_id BIGINT NOT NULL REFERENCES problem(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_private BOOLEAN NOT NULL DEFAULT TRUE,
    category VARCHAR(128),
    difficulty_override VARCHAR(20),
    submission_count INTEGER NOT NULL DEFAULT 0,
    ac_count INTEGER NOT NULL DEFAULT 0,
    added_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (classroom_id, problem_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_problem_classroom_visible
    ON classroom_problem(classroom_id, is_visible, display_order);

CREATE TABLE IF NOT EXISTS classroom_assignment (
    id VARCHAR(64) PRIMARY KEY,
    classroom_id VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    creator_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    allow_late_submission BOOLEAN NOT NULL DEFAULT FALSE,
    late_penalty DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    access_password VARCHAR(128),
    allowed_groups JSONB NOT NULL DEFAULT '[]'::jsonb,
    anti_cheating_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    allow_ai_tutor BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_classroom_time
    ON classroom_assignment(classroom_id, start_time, end_time);

CREATE TABLE IF NOT EXISTS classroom_assignment_section (
    id VARCHAR(64) PRIMARY KEY,
    assignment_id VARCHAR(64) NOT NULL REFERENCES classroom_assignment(id) ON DELETE CASCADE,
    title VARCHAR(128) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS classroom_assignment_problem (
    id VARCHAR(64) PRIMARY KEY,
    section_id VARCHAR(64) NOT NULL REFERENCES classroom_assignment_section(id) ON DELETE CASCADE,
    classroom_problem_id VARCHAR(64) NOT NULL REFERENCES classroom_problem(id) ON DELETE CASCADE,
    score DOUBLE PRECISION NOT NULL DEFAULT 10,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE (section_id, classroom_problem_id)
);

CREATE TABLE IF NOT EXISTS classroom_assignment_submission (
    id VARCHAR(64) PRIMARY KEY,
    assignment_id VARCHAR(64) NOT NULL REFERENCES classroom_assignment(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    submit_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    total_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_graded BOOLEAN NOT NULL DEFAULT FALSE,
    switch_window_count INTEGER NOT NULL DEFAULT 0,
    paste_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE (assignment_id, user_id)
);

CREATE TABLE IF NOT EXISTS classroom_assignment_problem_submission (
    id VARCHAR(64) PRIMARY KEY,
    submission_id VARCHAR(64) NOT NULL REFERENCES classroom_assignment_submission(id) ON DELETE CASCADE,
    assignment_problem_id VARCHAR(64) NOT NULL REFERENCES classroom_assignment_problem(id) ON DELETE CASCADE,
    answer TEXT NOT NULL DEFAULT '',
    code TEXT NOT NULL DEFAULT '',
    judge_score DOUBLE PRECISION,
    judge_status VARCHAR(32) NOT NULL DEFAULT 'Pending',
    ta_score DOUBLE PRECISION,
    ta_comment TEXT NOT NULL DEFAULT '',
    ta_graded_by_id BIGINT REFERENCES "user"(id) ON DELETE SET NULL,
    ta_graded_at TIMESTAMPTZ,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'Pending',
    ai_feedback TEXT NOT NULL DEFAULT '',
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_assignment_submission_assignment_user
    ON classroom_assignment_submission(assignment_id, user_id);

-- 学生课程进度（per user per language_pack）
CREATE TABLE IF NOT EXISTS learner_course_progress (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  language_pack_id BIGINT NOT NULL REFERENCES language_pack(id),
  current_chapter_id BIGINT REFERENCES language_pack_chapter(id),
  overall_mastery NUMERIC(5,4) DEFAULT 0,
  chapters_completed INTEGER DEFAULT 0,
  problems_attempted INTEGER DEFAULT 0,
  problems_solved INTEGER DEFAULT 0,
  last_activity_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (user_id, language_pack_id)
);

-- 学生 KC 级掌握度（per user per kc per language_pack）
CREATE TABLE IF NOT EXISTS learner_kc_mastery (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  language_pack_id BIGINT NOT NULL REFERENCES language_pack(id),
  kc_id BIGINT NOT NULL REFERENCES language_pack_kc(id),
  mastery NUMERIC(5,4) DEFAULT 0,
  attempt_count INTEGER DEFAULT 0,
  correct_count INTEGER DEFAULT 0,
  error_count INTEGER DEFAULT 0,
  last_attempt_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (user_id, language_pack_id, kc_id)
);
CREATE INDEX IF NOT EXISTS idx_kc_mastery_user_pack ON learner_kc_mastery(user_id, language_pack_id);

-- 冲刺计划
CREATE TABLE IF NOT EXISTS exam_sprint_plan (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  language_pack_id BIGINT NOT NULL REFERENCES language_pack(id),
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  target_date DATE,
  weak_kc_ids_json JSONB,
  plan_json JSONB NOT NULL,
  overall_readiness NUMERIC(5,4) DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- 冲刺任务条目
CREATE TABLE IF NOT EXISTS exam_sprint_task (
  id BIGSERIAL PRIMARY KEY,
  plan_id BIGINT NOT NULL REFERENCES exam_sprint_plan(id),
  chapter_id BIGINT REFERENCES language_pack_chapter(id),
  kc_id BIGINT REFERENCES language_pack_kc(id),
  task_type VARCHAR(32) NOT NULL,
  title TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  sort_order INTEGER NOT NULL DEFAULT 0,
  result_json JSONB,
  completed_at TIMESTAMPTZ
);

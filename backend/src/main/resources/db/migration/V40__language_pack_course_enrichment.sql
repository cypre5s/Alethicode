-- 语言包增加课程级字段
ALTER TABLE language_pack
  ADD COLUMN IF NOT EXISTS course_objective TEXT,
  ADD COLUMN IF NOT EXISTS target_audience TEXT DEFAULT '非计算机专业编程初学者',
  ADD COLUMN IF NOT EXISTS total_hours INTEGER;

-- 章节增加学习目标
ALTER TABLE language_pack_chapter
  ADD COLUMN IF NOT EXISTS learning_objective TEXT,
  ADD COLUMN IF NOT EXISTS estimated_hours NUMERIC(4,1);

-- KC 前驱关系（有向无环）
CREATE TABLE IF NOT EXISTS language_pack_kc_prerequisite (
  id BIGSERIAL PRIMARY KEY,
  kc_id BIGINT NOT NULL REFERENCES language_pack_kc(id),
  prerequisite_kc_id BIGINT NOT NULL REFERENCES language_pack_kc(id),
  language_pack_id BIGINT NOT NULL REFERENCES language_pack(id),
  UNIQUE (kc_id, prerequisite_kc_id)
);
CREATE INDEX IF NOT EXISTS idx_kc_prereq_pack ON language_pack_kc_prerequisite(language_pack_id);

-- 课程复习任务模板（发布时随语言包固化）
CREATE TABLE IF NOT EXISTS language_pack_review_task (
  id BIGSERIAL PRIMARY KEY,
  language_pack_id BIGINT NOT NULL REFERENCES language_pack(id),
  chapter_id BIGINT REFERENCES language_pack_chapter(id),
  kc_id BIGINT REFERENCES language_pack_kc(id),
  task_type VARCHAR(32) NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  problem_count INTEGER NOT NULL DEFAULT 3,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_review_task_pack ON language_pack_review_task(language_pack_id);

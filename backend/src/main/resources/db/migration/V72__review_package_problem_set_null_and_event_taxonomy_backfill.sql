-- V72: 复习包对题目失效的容错 + 学习事件分类回填 + 错题本 root_cause 脏数据治理
--
-- 背景：
--   1. ai_error_review_problem.problem_id ON DELETE CASCADE 导致原题被删时整行级联消失，
--      造成 ai_error_review_package.problem_count 与实际题数漂移、错题回顾整段空白。
--   2. ai_learning_event 在 V37 加入了 error_taxonomy 一等列，但写入 SQL 未补字段，
--      导致按 error_taxonomy 维度统计始终为 0。
--   3. ai_learner_notebook.root_cause 出现 "0" 等长度过短/纯数字脏数据，影响复习包"常见原因"展示。

-- ============================================================
-- 1. ai_error_review_problem: ON DELETE CASCADE → ON DELETE SET NULL
--    题目下架时保留学生历史，行仍存在但 problem_id = NULL
-- ============================================================

ALTER TABLE ai_error_review_problem
    DROP CONSTRAINT IF EXISTS ai_error_review_problem_problem_id_fkey;

ALTER TABLE ai_error_review_problem
    ALTER COLUMN problem_id DROP NOT NULL;

ALTER TABLE ai_error_review_problem
    ADD CONSTRAINT ai_error_review_problem_problem_id_fkey
    FOREIGN KEY (problem_id) REFERENCES problem(id) ON DELETE SET NULL;

COMMENT ON COLUMN ai_error_review_problem.problem_id IS
    'references problem(id); NULL 表示原题已下架，本行作为历史占位保留';

-- ============================================================
-- 2. 修正历史漂移：problem_count 与实际行数对齐
--    历史上已被 CASCADE 删除的行无法复原，至少让 problem_count 反映真相
-- ============================================================

UPDATE ai_error_review_package pkg SET
    problem_count = sub.actual_count,
    updated_at = NOW()
FROM (
    SELECT package_id, COUNT(*) AS actual_count
    FROM ai_error_review_problem
    GROUP BY package_id
) sub
WHERE pkg.id = sub.package_id
  AND pkg.problem_count <> sub.actual_count;

UPDATE ai_error_review_package pkg SET
    problem_count = 0,
    completed_count = 0,
    updated_at = NOW()
WHERE pkg.problem_count > 0
  AND NOT EXISTS (
      SELECT 1 FROM ai_error_review_problem rp WHERE rp.package_id = pkg.id
  );

-- ============================================================
-- 3. ai_learning_event: 历史数据按 extra_data 回填 error_taxonomy
--    新写入的 error_taxonomy 字段由应用层补齐
-- ============================================================

UPDATE ai_learning_event SET
    error_taxonomy = CASE
        WHEN (extra_data ->> 'error_taxonomy') IS NOT NULL THEN (extra_data ->> 'error_taxonomy')
        WHEN (extra_data ->> 'error_category') IS NOT NULL THEN (extra_data ->> 'error_category')
        ELSE error_taxonomy
    END
WHERE error_taxonomy IS NULL
  AND (
      (extra_data ->> 'error_taxonomy') IS NOT NULL
   OR (extra_data ->> 'error_category')  IS NOT NULL
  );

UPDATE ai_learning_event SET error_taxonomy = CASE error_taxonomy
    WHEN 'compile_error'  THEN 'syntax_error'
    WHEN 'compile'        THEN 'syntax_error'
    WHEN 'syntax'         THEN 'syntax_error'
    WHEN 'wrong_answer'   THEN 'logic_error'
    WHEN 'logic'          THEN 'logic_error'
    WHEN 'runtime'        THEN 'runtime_error'
    WHEN 'time_limit'     THEN 'performance'
    WHEN 'memory_limit'   THEN 'performance'
    WHEN 'system_error'   THEN 'unknown'
    WHEN 'unknown_error'  THEN 'unknown'
    WHEN 'type_error'     THEN 'name_or_type_error'
    WHEN 'name_error'     THEN 'name_or_type_error'
    ELSE error_taxonomy
END
WHERE error_taxonomy IS NOT NULL;

-- ============================================================
-- 4. ai_learner_notebook: 治理 root_cause 脏数据
--    把长度 < 2 或纯数字的 root_cause 替换为对应 error_taxonomy 的中文 label
-- ============================================================

UPDATE ai_learner_notebook SET root_cause = CASE error_taxonomy
    WHEN 'syntax_error'        THEN '语法错误'
    WHEN 'runtime_error'       THEN '运行时错误'
    WHEN 'logic_error'         THEN '逻辑错误'
    WHEN 'boundary_condition'  THEN '边界条件'
    WHEN 'performance'         THEN '性能不足'
    WHEN 'algorithm_error'     THEN '算法错误'
    WHEN 'input_parsing'       THEN '输入解析'
    WHEN 'name_or_type_error'  THEN '命名或类型错误'
    ELSE '未分类错误'
END
WHERE is_deleted = false
  AND (
      root_cause IS NULL
   OR length(btrim(root_cause)) < 2
   OR root_cause ~ '^[0-9]+$'
  );

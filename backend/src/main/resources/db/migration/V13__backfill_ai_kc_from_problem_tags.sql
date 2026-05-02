-- 回填 AI Tutor KC 基础数据：problem_tag -> ai_knowledge_component
-- 仅补齐缺失项，不覆盖已有 KC。
INSERT INTO ai_knowledge_component (id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess)
SELECT pt.id, pt.name, '', '', '', 0.3, 0.2, 0.1, 0.2
FROM problem_tag pt
LEFT JOIN ai_knowledge_component kc ON kc.id = pt.id
WHERE kc.id IS NULL;

-- 回填题目与 KC 关联：problem_problem_tags -> ai_problem_kc_mapping
INSERT INTO ai_problem_kc_mapping (problem_id, kc_id, weight)
SELECT ppt.problem_id, ppt.problemtag_id, 1.0
FROM problem_problem_tags ppt
JOIN problem p ON p.id = ppt.problem_id
JOIN ai_knowledge_component kc ON kc.id = ppt.problemtag_id
LEFT JOIN ai_problem_kc_mapping m
  ON m.problem_id = ppt.problem_id AND m.kc_id = ppt.problemtag_id
WHERE m.id IS NULL;

-- 兼容历史表：若库中存在 problem_tags（旧命名），同样回填到 ai_problem_kc_mapping
DO $$
BEGIN
  IF to_regclass('public.problem_tags') IS NOT NULL THEN
    INSERT INTO ai_problem_kc_mapping (problem_id, kc_id, weight)
    SELECT pt.problem_id, pt.problemtag_id, 1.0
    FROM problem_tags pt
    JOIN problem p ON p.id = pt.problem_id
    JOIN ai_knowledge_component kc ON kc.id = pt.problemtag_id
    LEFT JOIN ai_problem_kc_mapping m
      ON m.problem_id = pt.problem_id AND m.kc_id = pt.problemtag_id
    WHERE m.id IS NULL;
  END IF;
END $$;

SELECT setval(
  'ai_knowledge_component_id_seq',
  COALESCE((SELECT MAX(id) FROM ai_knowledge_component), 1),
  true
);

SELECT setval(
  'ai_problem_kc_mapping_id_seq',
  COALESCE((SELECT MAX(id) FROM ai_problem_kc_mapping), 1),
  true
);

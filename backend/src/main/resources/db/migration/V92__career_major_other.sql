-- 支持学生自定义专业：在 career_major_dictionary 中增加 "other" 条目。
-- 学生选择「其他」后可在学习目标字段中描述自己的具体专业。

INSERT INTO career_major_dictionary (code, name_zh, name_en, discipline, seed_keywords, seed_use_cases, seed_kcs, enabled)
VALUES (
    'other',
    '其他',
    'Other',
    '通用',
    '[]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    TRUE
)
ON CONFLICT (code) DO NOTHING;

-- Career Bridging Closure todo 15：用户级关闭面板（plan 9.3 节）
-- 注：V87 被并行的 chat composer phase plan 占用为 ai_tutor_session_token_usage，
--      本迁移让位 V88，行为不变。
--   学生可以在「我的」面板里独立关闭 4 个模块的任一个：
--     career_bridging_disabled  → 关闭里程碑式 Why 报告生成
--     coding_lens_disabled      → 关闭题面专业化重写
--     career_studio_disabled    → 关闭微项目工作室
--     career_path_disabled      → 关闭学习路径地图
--   默认全部 false（与 AlethicodeProperties.career.bridging.enabled=true 全局开关
--   叠加：global=true && user=false 才启用模块；任一关闭即跳过）。
--
-- 设计选择
--   按 plan 0 节强约束「不新建用户档案表」，本迁移仅扩展 user_profile，
--   未引入 user_career_preferences 单独表。4 个 boolean 列在 user_profile
--   行内，读写一次性命中无需 JOIN。

ALTER TABLE user_profile
    ADD COLUMN IF NOT EXISTS career_bridging_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS coding_lens_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS career_studio_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS career_path_disabled BOOLEAN NOT NULL DEFAULT FALSE;

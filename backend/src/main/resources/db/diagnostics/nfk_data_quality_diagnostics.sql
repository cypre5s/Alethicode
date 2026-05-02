-- NFK Phase A 数据质量诊断
--
-- 目标：在正式接入 NfkInferenceService 之前，对每个语言包的数据质量做一次体检。
-- 诊断范围对应 todos-three-remaining 阶段 3.1 的 A1-A4：
--   A1 题目 KC 覆盖率
--   A2 submission.result 枚举分布（确认 result=0 == AC）
--   A3 同学生 submission 的 create_time 真实性（TIMESTAMPTZ、非重复）
--   A4 ai_problem_kc_mapping 的 1:N 分布（决定是否按 weight 最大挑主 KC）
--
-- 使用：在正式环境 psql 里逐段执行；结果贴进 docs/reports/nfk-data-quality-report.md
-- 注意：所有查询只读，不写入任何表；统计数量级较小，建议每段单独 EXPLAIN 前先检查。

-- ============================================================
-- A1：每个 language_pack_id 的题目 KC 覆盖率
-- ============================================================
-- 含义：
--   * 如果 covered_problems / total_problems 太低（< 60%）说明 KC 映射不全，
--     NFK 的 skill_id 序列会严重稀疏，训练产出的 kt_prob 质量堪忧。
--   * weight > 0 且 kc_id not null 的才计入 covered。
WITH packs AS (
    SELECT lp.id               AS language_pack_id,
           lp.name,
           COUNT(m.problem_id) AS total_problems
    FROM language_pack lp
    LEFT JOIN language_pack_problem_mapping m ON m.language_pack_id = lp.id
    GROUP BY lp.id, lp.name
),
covered AS (
    SELECT pkm.language_pack_id,
           COUNT(DISTINCT akm.problem_id) AS covered_problems
    FROM language_pack_problem_mapping pkm
    JOIN ai_problem_kc_mapping akm
      ON akm.problem_id = pkm.problem_id
     AND akm.kc_id IS NOT NULL
     AND akm.weight > 0
    GROUP BY pkm.language_pack_id
)
SELECT p.language_pack_id,
       p.name,
       p.total_problems,
       COALESCE(c.covered_problems, 0) AS covered_problems,
       CASE
         WHEN p.total_problems = 0 THEN 0.0
         ELSE ROUND(COALESCE(c.covered_problems, 0)::numeric / p.total_problems, 4)
       END AS coverage
FROM packs p
LEFT JOIN covered c ON c.language_pack_id = p.language_pack_id
ORDER BY coverage ASC NULLS FIRST, p.total_problems DESC;

-- ============================================================
-- A2：submission.result 枚举分布（核对 result=0 == AC）
-- ============================================================
-- 含义：
--   * OJ 约定 result=0 为 AC；若实际分布里 result=1/2/... 占绝对多数，需要确认
--     labeler 是否把错题也算进 NFK 的 response=1 正样本。
--   * 这里统计全表；如果太大可以加 WHERE create_time > now() - interval '30 day'。
SELECT result,
       COUNT(*) AS cnt,
       ROUND(COUNT(*)::numeric / NULLIF(SUM(COUNT(*)) OVER (), 0), 4) AS ratio
FROM submission
GROUP BY result
ORDER BY cnt DESC;

-- ============================================================
-- A3：同学生 submission 的 create_time 真实性抽样
-- ============================================================
-- 含义：
--   * 如果 min/max 跨度极短（< 30 分钟）且 samples >= 20，说明数据可能是批量
--     灌入而非真实做题轨迹，NFK 的 delta_t 特征会被污染。
--   * 这里按 submission 数最多的前 5 个学生抽样。
WITH top_users AS (
    SELECT user_id, COUNT(*) AS cnt
    FROM submission
    WHERE user_id > 0
    GROUP BY user_id
    ORDER BY cnt DESC
    LIMIT 5
)
SELECT s.user_id,
       COUNT(*)                              AS samples,
       MIN(s.create_time)                    AS first_ts,
       MAX(s.create_time)                    AS last_ts,
       EXTRACT(EPOCH FROM (MAX(s.create_time) - MIN(s.create_time)))::bigint AS span_seconds,
       COUNT(DISTINCT s.create_time)         AS distinct_ts,
       COUNT(*) - COUNT(DISTINCT s.create_time) AS duplicate_ts
FROM submission s
JOIN top_users t ON t.user_id = s.user_id
GROUP BY s.user_id
ORDER BY samples DESC;

-- ============================================================
-- A4：ai_problem_kc_mapping 的 1:N 分布
-- ============================================================
-- 含义：
--   * 如果 N 主要集中在 1（单 KC 一个题）则 NFK 直接用 kc_id；
--   * 如果 N>=2 的题占比较高（> 20%），需要在导出训练数据时按 weight DESC 挑主 KC，
--     否则同一题会被拆成多条训练样本，污染序列顺序。
SELECT kc_count,
       COUNT(*) AS problem_count,
       ROUND(COUNT(*)::numeric / NULLIF(SUM(COUNT(*)) OVER (), 0), 4) AS ratio
FROM (
    SELECT problem_id, COUNT(*) AS kc_count
    FROM ai_problem_kc_mapping
    WHERE weight > 0 AND kc_id IS NOT NULL
    GROUP BY problem_id
) agg
GROUP BY kc_count
ORDER BY kc_count ASC;

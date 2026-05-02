#!/usr/bin/env bash
# ============================================================
# AI 展示种子数据注入脚本（幂等）
#
# 用法:
#   bash scripts/seed/seed_ai_showcase.sh
#   bash scripts/seed/seed_ai_showcase.sh --db-url "postgresql://user:pass@host:port/db"
#
# 前提:
#   - PostgreSQL 中已运行过 Flyway 迁移（至少 V52）
#   - 存在演示用语言包和题目
# ============================================================
set -euo pipefail

DB_URL="${1:-postgresql://root:root123456@127.0.0.1:5436/alethicode}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "=== AI 展示种子数据注入 ==="
log "数据库: $DB_URL"

psql "$DB_URL" <<'SEED_SQL'

-- ============================================================
-- 1. 查找可用的演示学生和语言包
-- ============================================================

DO $$
DECLARE
    v_user_id        BIGINT;
    v_lp_id          BIGINT;
    v_problem_id     BIGINT;
    v_session_id     VARCHAR(64);
    v_trace_id       VARCHAR(64);
    v_kc_id          BIGINT;
BEGIN

-- 取第一个普通用户作为演示学生
SELECT id INTO v_user_id FROM "user" WHERE admin_type = 'Regular User' LIMIT 1;
IF v_user_id IS NULL THEN
    RAISE NOTICE 'No regular user found, skipping seed';
    RETURN;
END IF;
RAISE NOTICE 'Demo user: %', v_user_id;

-- 取第一个语言包
SELECT id INTO v_lp_id FROM language_pack WHERE status = 'published' LIMIT 1;
IF v_lp_id IS NULL THEN
    SELECT id INTO v_lp_id FROM language_pack LIMIT 1;
END IF;
IF v_lp_id IS NULL THEN
    RAISE NOTICE 'No language_pack found, skipping seed';
    RETURN;
END IF;
RAISE NOTICE 'Demo language_pack: %', v_lp_id;

-- 取一个题目
SELECT id INTO v_problem_id FROM problem WHERE is_public = true LIMIT 1;
IF v_problem_id IS NULL THEN
    SELECT id INTO v_problem_id FROM problem LIMIT 1;
END IF;
RAISE NOTICE 'Demo problem: %', v_problem_id;

-- ============================================================
-- 1.5 确保演示题目有 KC 映射
-- ============================================================

INSERT INTO ai_problem_kc_mapping (problem_id, kc_id, weight)
SELECT v_problem_id, synced_ai_kc_id, 1.0
FROM language_pack_kc
WHERE language_pack_id = v_lp_id AND synced_ai_kc_id IS NOT NULL
LIMIT 3
ON CONFLICT (problem_id, kc_id) DO NOTHING;
RAISE NOTICE 'KC mappings seeded for problem %', v_problem_id;

-- ============================================================
-- 2. 写入 KC 掌握度（2 弱 + 3 强 + 3 中等）
-- ============================================================

-- 查找可用的 KC
FOR v_kc_id IN
    SELECT id FROM language_pack_kc WHERE language_pack_id = v_lp_id LIMIT 8
LOOP
    INSERT INTO learner_kc_mastery (user_id, language_pack_id, kc_id, mastery, attempt_count, correct_count, error_count, last_attempt_at, updated_at)
    VALUES (
        v_user_id, v_lp_id, v_kc_id,
        CASE
            WHEN v_kc_id % 8 < 2 THEN 0.2 + random() * 0.15  -- 弱
            WHEN v_kc_id % 8 < 5 THEN 0.8 + random() * 0.15  -- 强
            ELSE 0.45 + random() * 0.2                         -- 中等
        END,
        (5 + random() * 20)::int,
        (3 + random() * 15)::int,
        (1 + random() * 5)::int,
        NOW() - interval '1 day' * (random() * 7)::int,
        NOW()
    )
    ON CONFLICT (user_id, language_pack_id, kc_id) DO UPDATE SET
        mastery = EXCLUDED.mastery,
        updated_at = NOW();
END LOOP;
RAISE NOTICE 'KC mastery seeded';

-- ============================================================
-- 3. 写入学习者记忆
-- ============================================================

INSERT INTO ai_learner_memory (user_id, memory_key, memory_type, memory_value, confidence, created_at, updated_at)
VALUES
    (v_user_id, 'error_pattern_loop_boundary', 'error_pattern',
     '多次在 for 循环的 range 上界多写 1，导致 IndexError 或多计算一次',
     0.86, NOW() - interval '5 days', NOW()),

    (v_user_id, 'error_pattern_off_by_one', 'error_pattern',
     '在列表索引和循环范围中反复出现 off-by-one 错误',
     0.78, NOW() - interval '3 days', NOW()),

    (v_user_id, 'error_pattern_indent', 'error_pattern',
     '偶尔把循环体外的语句错误缩进到循环体内',
     0.62, NOW() - interval '7 days', NOW()),

    (v_user_id, 'strategy_pref_example', 'teaching_strategy_preference',
     '偏好先看完整示例再做题',
     0.72, NOW() - interval '10 days', NOW()),

    (v_user_id, 'strategy_pref_stepbystep', 'teaching_strategy_preference',
     '对逐步引导式讲解评价较高',
     0.65, NOW() - interval '8 days', NOW())
ON CONFLICT (user_id, memory_key) DO NOTHING;
RAISE NOTICE 'Learner memory seeded';

-- ============================================================
-- 4. 写入课程进度
-- ============================================================

INSERT INTO learner_course_progress (user_id, language_pack_id, overall_mastery, problems_attempted, problems_solved, last_activity_at)
VALUES (v_user_id, v_lp_id, 0.62, 18, 12, NOW())
ON CONFLICT (user_id, language_pack_id) DO UPDATE SET
    overall_mastery = 0.62,
    problems_attempted = 18,
    problems_solved = 12,
    last_activity_at = NOW();
RAISE NOTICE 'Course progress seeded';

-- ============================================================
-- 5. 写入 Trace 样本（完整 span 链）
-- ============================================================

v_session_id := 'showcase_session_' || v_user_id;
v_trace_id := 'showcase_trace_001';

INSERT INTO ai_workflow_session (session_id, thread_id, user_id, problem_id, phase, is_active)
VALUES (v_session_id, 'showcase_thread_1', v_user_id, v_problem_id, 'ERROR_FEEDBACK', false)
ON CONFLICT (session_id) DO NOTHING;

-- Trace spans
INSERT INTO ai_workflow_event (session_id, event_type, trace_id, event_data, created_at) VALUES
    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"DISPATCH","agent_name":"OrchestratorAgent","duration_ms":3,"summary":"→ DiagnosticsAgent"}'::jsonb,
     NOW() - interval '1 hour'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"EVIDENCE_ASSEMBLY","agent_name":"DiagnosticsAgent","duration_ms":42,"summary":"problem + submission + courseware"}'::jsonb,
     NOW() - interval '1 hour' + interval '3 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"MEMORY_RECALL","agent_name":"DiagnosticsAgent","duration_ms":28,"summary":"3 memories recalled, confidence=[0.86, 0.78, 0.62]"}'::jsonb,
     NOW() - interval '1 hour' + interval '45 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"LLM_CALL","agent_name":"DiagnosticsAgent","duration_ms":1820,"summary":"deepseek-v4-flash, 1200 tokens"}'::jsonb,
     NOW() - interval '1 hour' + interval '73 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"TOOL_CALL","agent_name":"DiagnosticsAgent","tool_name":"get_learner_history","duration_ms":35,"summary":"读取本题 3 次提交"}'::jsonb,
     NOW() - interval '1 hour' + interval '1893 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"TOOL_CALL","agent_name":"DiagnosticsAgent","tool_name":"search_similar_errors","duration_ms":18,"summary":"匹配 2 个相似错误"}'::jsonb,
     NOW() - interval '1 hour' + interval '1928 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"LLM_CALL","agent_name":"DiagnosticsAgent","duration_ms":380,"summary":"deepseek-v4-flash, 600 tokens (reflection)"}'::jsonb,
     NOW() - interval '1 hour' + interval '1946 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"GUARDRAIL","agent_name":"DiagnosticsAgent","duration_ms":5,"summary":"schema validation passed"}'::jsonb,
     NOW() - interval '1 hour' + interval '2326 ms'),

    (v_session_id, 'trace_span', v_trace_id,
     '{"span_type":"OUTPUT","agent_name":"DiagnosticsAgent","duration_ms":3,"summary":"error_diagnosis card emitted"}'::jsonb,
     NOW() - interval '1 hour' + interval '2331 ms');

RAISE NOTICE 'Trace sample seeded: %', v_trace_id;

-- ============================================================
-- 6. 写入 Eval 样本（8 维评分）
-- ============================================================

INSERT INTO ai_workflow_event (session_id, event_type, trace_id, event_data, created_at) VALUES
    (v_session_id, 'eval_score', v_trace_id,
     '{"card_type":"error_diagnosis","overall_score":0.87,"dimension_scores":{"FACTUAL_CORRECTNESS":0.92,"PEDAGOGICAL_FIT":0.84,"ANSWER_LEAKAGE":0.99,"KC_ALIGNMENT":0.88,"GUIDANCE_QUALITY":0.81,"SCAFFOLD_LEVEL_MATCH":0.86,"COMPREHENSIBILITY":0.90,"ENCOURAGEMENT":0.76},"sample_source":"seed"}'::jsonb,
     NOW() - interval '1 hour'),

    (v_session_id, 'eval_score', 'showcase_trace_002',
     '{"card_type":"problem_guide","overall_score":0.82,"dimension_scores":{"FACTUAL_CORRECTNESS":0.88,"PEDAGOGICAL_FIT":0.79,"ANSWER_LEAKAGE":0.98,"KC_ALIGNMENT":0.85,"GUIDANCE_QUALITY":0.78,"SCAFFOLD_LEVEL_MATCH":0.82,"COMPREHENSIBILITY":0.86,"ENCOURAGEMENT":0.72},"sample_source":"seed"}'::jsonb,
     NOW() - interval '2 hours'),

    (v_session_id, 'eval_score', 'showcase_trace_003',
     '{"card_type":"ideation_analysis","overall_score":0.79,"dimension_scores":{"FACTUAL_CORRECTNESS":0.85,"PEDAGOGICAL_FIT":0.75,"ANSWER_LEAKAGE":0.97,"KC_ALIGNMENT":0.80,"GUIDANCE_QUALITY":0.74,"SCAFFOLD_LEVEL_MATCH":0.79,"COMPREHENSIBILITY":0.84,"ENCOURAGEMENT":0.68},"sample_source":"seed"}'::jsonb,
     NOW() - interval '3 hours');

RAISE NOTICE 'Eval samples seeded';

-- ============================================================
-- 7. 写入 Agent 调用事件样本（用于 Overview 看板）
-- ============================================================

INSERT INTO ai_workflow_event (session_id, event_type, trace_id, event_data, created_at) VALUES
    (v_session_id, 'agent_call', v_trace_id,
     '{"agent_name":"DiagnosticsAgent","agent_status":"OK","agent_duration_ms":2340,"memory_used":"true"}'::jsonb,
     NOW() - interval '1 hour'),

    (v_session_id, 'agent_call', 'showcase_trace_002',
     '{"agent_name":"GuideAgent","agent_status":"OK","agent_duration_ms":980,"memory_used":"true"}'::jsonb,
     NOW() - interval '2 hours'),

    (v_session_id, 'agent_call', 'showcase_trace_003',
     '{"agent_name":"MetacognitiveAgent","agent_status":"OK","agent_duration_ms":1520,"memory_used":"false"}'::jsonb,
     NOW() - interval '3 hours');

RAISE NOTICE 'Agent call events seeded';

-- ============================================================
-- 8. 写入演示提交记录（混合 AC/WA/CE）
-- ============================================================

INSERT INTO submission (id, problem_id, user_id, username, code, result, language, create_time) VALUES
    ('showcase_sub_01', v_problem_id, v_user_id, 'demo', 'for i in range(1, n+1):\n    print(i)', 0, 'Python3', NOW() - interval '6 days'),
    ('showcase_sub_02', v_problem_id, v_user_id, 'demo', 'for i in range(1, n):\n    print(i)', -1, 'Python3', NOW() - interval '6 days' + interval '3 minutes'),
    ('showcase_sub_03', v_problem_id, v_user_id, 'demo', 'for i in range(n+1):\n    print(i)', -1, 'Python3', NOW() - interval '5 days'),
    ('showcase_sub_04', v_problem_id, v_user_id, 'demo', 'for i in range(1, n+1):\n    print(i)', 0, 'Python3', NOW() - interval '5 days' + interval '8 minutes'),
    ('showcase_sub_05', v_problem_id, v_user_id, 'demo', 'n = int(input())\nfor i in range(n)', -2, 'Python3', NOW() - interval '4 days'),
    ('showcase_sub_06', v_problem_id, v_user_id, 'demo', 'n = int(input())\nfor i in range(n):\n    print(i+1)', 0, 'Python3', NOW() - interval '4 days' + interval '5 minutes'),
    ('showcase_sub_07', v_problem_id, v_user_id, 'demo', 'a = list(map(int, input().split()))\nprint(a[len(a)])', -1, 'Python3', NOW() - interval '3 days'),
    ('showcase_sub_08', v_problem_id, v_user_id, 'demo', 'a = list(map(int, input().split()))\nprint(a[-1])', 0, 'Python3', NOW() - interval '3 days' + interval '10 minutes'),
    ('showcase_sub_09', v_problem_id, v_user_id, 'demo', 'print(sum(range(1,n+1)))', -1, 'Python3', NOW() - interval '1 day'),
    ('showcase_sub_10', v_problem_id, v_user_id, 'demo', 'n=int(input())\nprint(sum(range(1,n+1)))', 0, 'Python3', NOW() - interval '1 day' + interval '4 minutes')
ON CONFLICT (id) DO NOTHING;
RAISE NOTICE 'Demo submissions seeded';

RAISE NOTICE '=== Seed complete! user_id=%, lp_id=%, problem_id=% ===', v_user_id, v_lp_id, v_problem_id;

END $$;

SEED_SQL

log "=== 种子数据注入完成 ==="

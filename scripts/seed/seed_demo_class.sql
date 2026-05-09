-- ============================================================
-- Python 课程包公测演示班级 + 25 名学生 + 学习/AI/反馈数据
--
-- 用途：为后台「学生学习数据 / 辅导总控 / 公测反馈」三个面板注入
--      可观测的演示数据。脚本幂等：每次运行先按演示标识清理已写入
--      数据，再重新生成。
--
-- 演示标识：
--   classroom.id    = 'demo_python_class_2026'
--   user.username   LIKE 'demo_stu_%'
--   submission.id   LIKE 'demo_sub_%'
--   session_id      LIKE 'demo_sess_%'
--   beta_feedback_report.description LIKE '[demo-seed] %'
--
-- 默认密码：Alethicode2026!（bcrypt 由 pgcrypto 生成）
--
-- 需要：
--   - language_pack id=43 已发布且至少有 6 道关联题目
--   - "user" 中存在管理员（id=1 root）
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $demo$
DECLARE
    v_admin_id        BIGINT;
    v_lp_id           BIGINT := 43;
    v_class_id        VARCHAR(64) := 'demo_python_class_2026';
    v_class_name      TEXT := 'Python 公测演示班 (Demo)';
    v_password_hash   TEXT;
    v_student_count   INTEGER := 25;
    v_problem_ids     BIGINT[];
    v_kc_ids          BIGINT[];
    v_problem_count   INTEGER;
    v_kc_count        INTEGER;
    v_student_id      BIGINT;
    v_username        TEXT;
    v_problem_id      BIGINT;
    v_session_id      VARCHAR(64);
    v_run_id          VARCHAR(128);
    v_thread_id       VARCHAR(128);
    v_trace_id        VARCHAR(128);
    v_phase           TEXT;
    v_card_type       TEXT;
    v_failure_bucket  TEXT;
    v_attempt_offset  INTEGER;
    v_attempt_total   INTEGER;
    v_first_ac_at     TIMESTAMPTZ;
    v_sub_id          TEXT;
    v_sub_time        TIMESTAMPTZ;
    v_sub_result      INTEGER;
    v_diag_time       TIMESTAMPTZ;
    v_post_ac_time    TIMESTAMPTZ;
    v_kc_idx          INTEGER;
    v_kc_id           BIGINT;
    v_mastery         NUMERIC(5,4);
    v_attempts_done   INTEGER;
    v_corrects_done   INTEGER;
    v_solved_set      INTEGER;
    v_attempt_set     INTEGER;
    v_overall_mastery NUMERIC(5,4);
    v_sub_idx         INTEGER;
    v_session_idx     INTEGER;
    v_session_total   INTEGER;
    v_chosen_problem  INTEGER;
    v_quality_hour    INTEGER;
    v_avg_score       NUMERIC(5,4);
    v_card_seq        INTEGER;
BEGIN
    SELECT id INTO v_admin_id FROM "user"
        WHERE admin_type = 'Admin' AND username = 'root'
        LIMIT 1;
    IF v_admin_id IS NULL THEN
        SELECT id INTO v_admin_id FROM "user" WHERE admin_type = 'Admin' ORDER BY id LIMIT 1;
    END IF;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'demo-seed: no admin user found, abort';
    END IF;

    PERFORM 1 FROM language_pack WHERE id = v_lp_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'demo-seed: language_pack id=% not found, abort', v_lp_id;
    END IF;

    SELECT array_agg(problem_id ORDER BY problem_id)
      INTO v_problem_ids
      FROM (
          SELECT problem_id
            FROM language_pack_problem_mapping
           WHERE language_pack_id = v_lp_id
           ORDER BY problem_id
           LIMIT 6
      ) t;
    v_problem_count := COALESCE(array_length(v_problem_ids, 1), 0);
    IF v_problem_count < 6 THEN
        RAISE EXCEPTION 'demo-seed: language_pack % only has % problems (need >=6)', v_lp_id, v_problem_count;
    END IF;

    SELECT array_agg(id ORDER BY id)
      INTO v_kc_ids
      FROM (
          SELECT id FROM language_pack_kc
           WHERE language_pack_id = v_lp_id
           ORDER BY id
           LIMIT 8
      ) t;
    v_kc_count := COALESCE(array_length(v_kc_ids, 1), 0);
    IF v_kc_count = 0 THEN
        RAISE EXCEPTION 'demo-seed: language_pack % has no KC, abort', v_lp_id;
    END IF;

    -- ----------------------------------------------------------
    -- 0. 清理之前的演示数据（按标识，幂等）
    -- ----------------------------------------------------------
    DELETE FROM ai_tutor_workflow_event WHERE session_id LIKE 'demo_sess_%';
    DELETE FROM ai_tutor_workflow_session WHERE session_id LIKE 'demo_sess_%';
    DELETE FROM beta_feedback_report
        WHERE description LIKE '[demo-seed] %'
           OR reporter_user_id IN (SELECT id FROM "user" WHERE username LIKE 'demo_stu_%');
    DELETE FROM submission WHERE id LIKE 'demo_sub_%';
    DELETE FROM learner_kc_mastery
        WHERE language_pack_id = v_lp_id
          AND user_id IN (SELECT id FROM "user" WHERE username LIKE 'demo_stu_%');
    DELETE FROM learner_course_progress
        WHERE language_pack_id = v_lp_id
          AND user_id IN (SELECT id FROM "user" WHERE username LIKE 'demo_stu_%');
    DELETE FROM classroom_member WHERE classroom_id = v_class_id;
    DELETE FROM classroom_language_pack WHERE classroom_id = v_class_id;
    DELETE FROM classroom WHERE id = v_class_id;
    -- 学生用户保留（其它表有 ON DELETE CASCADE，删除会扩散），仅在不存在时新建。

    -- ----------------------------------------------------------
    -- 1. 创建班级 + 绑定 Python 课程包
    -- ----------------------------------------------------------
    INSERT INTO classroom (id, name, description, course_code, semester,
                           created_by_id, is_active, allow_student_view_others,
                           enable_ai_tutor, enable_collaboration,
                           current_chapter, chapter_unlock_threshold,
                           member_count, problem_count, lesson_count,
                           create_time, update_time)
    VALUES (v_class_id, v_class_name,
            'Alethicode 公测演示用班级，用于在管理后台演示学生学习数据、辅导总控、公测反馈三个面板。',
            'DEMO-PY-2026', '2026 春',
            v_admin_id, TRUE, TRUE, TRUE, TRUE,
            1, 0.7,
            v_student_count, v_problem_count, 0,
            NOW() - INTERVAL '14 days', NOW());

    INSERT INTO classroom_language_pack (classroom_id, language_pack_id, create_time)
    VALUES (v_class_id, v_lp_id, NOW() - INTERVAL '14 days');

    -- ----------------------------------------------------------
    -- 2. 注入 25 名学生（不存在则建，存在复用）
    -- ----------------------------------------------------------
    v_password_hash := crypt('Alethicode2026!', gen_salt('bf', 10));

    FOR i IN 1..v_student_count LOOP
        v_username := 'demo_stu_' || lpad(i::text, 3, '0');
        INSERT INTO "user" (username, admin_type, problem_permission,
                            is_disabled, password_hash, email,
                            two_factor_auth, session_keys, open_api,
                            create_time)
        VALUES (v_username, 'Regular User', 'None',
                FALSE, v_password_hash,
                v_username || '@demo.alethicode.local',
                FALSE, '[]'::jsonb, FALSE,
                NOW() - INTERVAL '14 days')
        ON CONFLICT (username) DO UPDATE SET
            admin_type = EXCLUDED.admin_type,
            problem_permission = EXCLUDED.problem_permission,
            is_disabled = EXCLUDED.is_disabled,
            password_hash = EXCLUDED.password_hash,
            email = EXCLUDED.email
        RETURNING id INTO v_student_id;

        INSERT INTO classroom_member (id, classroom_id, user_id, role, join_method,
                                      nickname, student_id, problems_solved,
                                      last_active_time, join_time, update_time)
        VALUES (v_class_id || '_' || lpad(i::text, 3, '0'),
                v_class_id, v_student_id, 'student', 'invited',
                '演示学生 ' || lpad(i::text, 3, '0'),
                'DEMO' || lpad(i::text, 3, '0'),
                0,
                NOW() - INTERVAL '1 hour' * ((i % 12) + 1),
                NOW() - INTERVAL '14 days',
                NOW());
    END LOOP;

    -- ----------------------------------------------------------
    -- 3. 为每个学生生成提交、AI session/event、KC 掌握度、course_progress、反馈
    -- ----------------------------------------------------------
    FOR i IN 1..v_student_count LOOP
        v_username := 'demo_stu_' || lpad(i::text, 3, '0');
        SELECT id INTO v_student_id FROM "user" WHERE username = v_username;

        v_attempts_done := 0;
        v_corrects_done := 0;
        v_solved_set := 0;
        v_attempt_set := 0;

        -- 题目尝试：每位学生覆盖前 6 道（v_problem_ids），
        -- 学生 i 中后 (i % 4) 道留作未通过，模拟尚在学习中。
        FOR pidx IN 1..v_problem_count LOOP
            v_problem_id := v_problem_ids[pidx];
            v_attempt_total := 3 + ((i + pidx) % 5);                  -- 3..7 次提交
            v_first_ac_at := NULL;
            v_attempt_set := v_attempt_set + 1;

            FOR sidx IN 1..v_attempt_total LOOP
                v_sub_id := 'demo_sub_'
                            || lpad(i::text, 3, '0') || '_'
                            || lpad(pidx::text, 2, '0') || '_'
                            || lpad(sidx::text, 2, '0');
                v_sub_time := NOW()
                              - INTERVAL '1 day' * ((7 - ((i + pidx) % 7))::int)
                              + INTERVAL '11 minutes' * sidx
                              + INTERVAL '1 minute' * ((i + pidx + sidx) % 9);

                IF sidx = v_attempt_total
                   AND ((i + pidx) % 4) <> 0 THEN
                    v_sub_result := 0;            -- AC（最后一次）
                ELSIF sidx = v_attempt_total - 1
                      AND v_attempt_total >= 4
                      AND ((i + pidx) % 5) = 0 THEN
                    v_sub_result := 0;            -- 部分中段 AC，触发 “首次 AC”
                ELSE
                    -- 失败结果在 -1（WA）/ -2（CE）/ 1（CPU TLE）/ 4（RE）之间轮换
                    v_sub_result := CASE ((sidx + i) % 4)
                                        WHEN 0 THEN -1
                                        WHEN 1 THEN -2
                                        WHEN 2 THEN 1
                                        ELSE 4
                                    END;
                END IF;

                INSERT INTO submission (id, problem_id, user_id, username,
                                        code, result, info, language, shared,
                                        statistic_info, ip, create_time)
                VALUES (v_sub_id, v_problem_id, v_student_id, v_username,
                        '# demo seed code\n# attempt ' || sidx || ' for problem ' || v_problem_id,
                        v_sub_result,
                        jsonb_build_object('demo_seed', true, 'attempt', sidx),
                        'Python3', FALSE,
                        jsonb_build_object('time_cost', 30 + ((i * sidx) % 70),
                                           'memory_cost', 8000 + ((i + sidx * 13) % 6000)),
                        '127.0.0.1',
                        v_sub_time);

                v_attempts_done := v_attempts_done + 1;
                IF v_sub_result = 0 THEN
                    v_corrects_done := v_corrects_done + 1;
                    IF v_first_ac_at IS NULL THEN
                        v_first_ac_at := v_sub_time;
                        v_solved_set := v_solved_set + 1;
                    END IF;
                END IF;
            END LOOP;
        END LOOP;

        -- ------------------------------------------------------
        -- 3.1 KC 掌握度：覆盖前 5 个 KC，强弱混合
        -- ------------------------------------------------------
        FOR kidx IN 1..LEAST(v_kc_count, 5) LOOP
            v_kc_id := v_kc_ids[kidx];
            v_mastery := CASE
                WHEN ((i + kidx) % 5) = 0 THEN 0.20 + ((i % 7) * 0.02)::numeric
                WHEN ((i + kidx) % 5) IN (1, 2) THEN 0.78 + ((i % 5) * 0.03)::numeric
                ELSE 0.50 + ((i % 6) * 0.04)::numeric
            END;
            INSERT INTO learner_kc_mastery (user_id, language_pack_id, kc_id,
                                            mastery, attempt_count, correct_count, error_count,
                                            last_attempt_at, updated_at)
            VALUES (v_student_id, v_lp_id, v_kc_id,
                    v_mastery,
                    8 + ((i + kidx) % 12),
                    4 + ((i + kidx) % 7),
                    1 + ((i + kidx) % 4),
                    NOW() - INTERVAL '1 day' * ((i + kidx) % 6),
                    NOW())
            ON CONFLICT (user_id, language_pack_id, kc_id) DO UPDATE SET
                mastery = EXCLUDED.mastery,
                attempt_count = EXCLUDED.attempt_count,
                correct_count = EXCLUDED.correct_count,
                error_count = EXCLUDED.error_count,
                last_attempt_at = EXCLUDED.last_attempt_at,
                updated_at = NOW();
        END LOOP;

        v_overall_mastery := LEAST(0.95, GREATEST(0.10,
            (v_corrects_done::numeric / GREATEST(v_attempts_done, 1)) * 0.7
            + 0.25));

        INSERT INTO learner_course_progress (user_id, language_pack_id, current_chapter_id,
                                             overall_mastery, chapters_completed,
                                             problems_attempted, problems_solved,
                                             last_activity_at, created_at, updated_at)
        VALUES (v_student_id, v_lp_id, NULL,
                v_overall_mastery, ((i % 4) + 1),
                v_attempt_set, v_solved_set,
                NOW() - INTERVAL '1 hour' * ((i % 18) + 1),
                NOW() - INTERVAL '14 days', NOW())
        ON CONFLICT (user_id, language_pack_id) DO UPDATE SET
            overall_mastery = EXCLUDED.overall_mastery,
            problems_attempted = EXCLUDED.problems_attempted,
            problems_solved = EXCLUDED.problems_solved,
            last_activity_at = EXCLUDED.last_activity_at,
            updated_at = NOW();

        -- ------------------------------------------------------
        -- 3.2 AI Tutor session / event：3-5 个 session，覆盖 6 道题
        -- ------------------------------------------------------
        v_session_total := 3 + (i % 3);     -- 3..5 sessions
        FOR sidx IN 1..v_session_total LOOP
            v_chosen_problem := ((i + sidx) % v_problem_count) + 1;
            v_problem_id := v_problem_ids[v_chosen_problem];
            v_session_id := 'demo_sess_'
                            || lpad(i::text, 3, '0') || '_'
                            || lpad(sidx::text, 2, '0');
            v_run_id    := 'demo_run_'
                            || lpad(i::text, 3, '0') || '_'
                            || lpad(sidx::text, 2, '0');
            v_thread_id := 'demo_thread_'
                            || lpad(i::text, 3, '0') || '_'
                            || lpad(sidx::text, 2, '0');
            v_trace_id  := 'demo_trace_'
                            || lpad(i::text, 3, '0') || '_'
                            || lpad(sidx::text, 2, '0');

            v_phase := CASE sidx % 4
                          WHEN 0 THEN 'READING'
                          WHEN 1 THEN 'IDEATING'
                          WHEN 2 THEN 'ERROR_FEEDBACK'
                          ELSE 'AC_REVIEW'
                       END;

            INSERT INTO ai_tutor_workflow_session (
                session_id, thread_id, user_id, problem_id,
                phase, runtime_state, pending_human_action,
                node_outputs, behavior_metrics, available_actions,
                last_checkpoint_id, last_run_id, is_active,
                created_at, updated_at,
                language, plan, recommendation_reason,
                failure_bucket, last_error,
                active_mode, last_mode_switched_at,
                tokens_used, tokens_limit, model_name,
                compact_count
            )
            VALUES (
                v_session_id, v_thread_id, v_student_id, v_problem_id,
                v_phase,
                CASE WHEN (sidx + i) % 9 = 0 THEN 'FAILED' ELSE 'COMPLETED' END,
                '',
                '{}'::jsonb, '{}'::jsonb,
                jsonb_build_array(jsonb_build_object('key', 'reading', 'label', '题目导读'),
                                  jsonb_build_object('key', 'ideating', 'label', '思路分析')),
                'demo_ckpt_' || lpad(i::text, 3, '0') || '_' || lpad(sidx::text, 2, '0'),
                v_run_id, FALSE,
                NOW() - INTERVAL '1 day' * ((i + sidx) % 6) - INTERVAL '13 minutes' * sidx,
                NOW() - INTERVAL '1 day' * ((i + sidx) % 6) - INTERVAL '5 minutes' * sidx,
                'Python3', '{}'::jsonb, '',
                CASE WHEN (sidx + i) % 9 = 0
                     THEN (ARRAY['SCHEMA_VIOLATION','SYSTEM_ERROR','TOOL_EXECUTION_FAILED'])[(sidx + i) % 3 + 1]
                     ELSE NULL
                END,
                '', 'reading', NOW(),
                900 + (i * sidx * 11) % 6000, 8000, 'deepseek-v4-flash',
                0
            );

            -- trace_span 事件（DISPATCH / MEMORY_RECALL / LLM_CALL / OUTPUT）
            INSERT INTO ai_tutor_workflow_event (
                session_id, run_id, thread_id, event_type,
                runtime_state, server_event, client_event, failure_bucket,
                trace_id, event_data, created_at, card_id, card_type,
                mode_when_produced, referenced_card_ids
            )
            VALUES
            (v_session_id, v_run_id, v_thread_id, 'trace_span',
             NULL, NULL, NULL, NULL, v_trace_id,
             jsonb_build_object(
                'span_type', 'DISPATCH',
                'duration_ms', 4 + ((i + sidx) % 8),
                'status', 'OK',
                'metadata', jsonb_build_object('agent', v_phase)
             ),
             NOW() - INTERVAL '1 day' * ((i + sidx) % 6) - INTERVAL '13 minutes' * sidx,
             NULL, NULL, 'reading', '[]'::jsonb),
            (v_session_id, v_run_id, v_thread_id, 'trace_span',
             NULL, NULL, NULL, NULL, v_trace_id,
             jsonb_build_object(
                'span_type', 'MEMORY_RECALL',
                'duration_ms', 18 + ((i + sidx) % 30),
                'status', CASE WHEN (i + sidx) % 7 = 0 THEN 'FAILED' ELSE 'OK' END,
                'metadata', jsonb_build_object('agent', v_phase, 'top_k', 3)
             ),
             NOW() - INTERVAL '1 day' * ((i + sidx) % 6) - INTERVAL '13 minutes' * sidx + INTERVAL '120 ms',
             NULL, NULL, 'reading', '[]'::jsonb),
            (v_session_id, v_run_id, v_thread_id, 'trace_span',
             NULL, NULL, NULL, NULL, v_trace_id,
             jsonb_build_object(
                'span_type', 'LLM_CALL',
                'duration_ms', 850 + ((i * sidx) % 1500),
                'status', 'OK',
                'metadata', jsonb_build_object('agent', v_phase, 'model', 'deepseek-v4-flash')
             ),
             NOW() - INTERVAL '1 day' * ((i + sidx) % 6) - INTERVAL '13 minutes' * sidx + INTERVAL '350 ms',
             NULL, NULL, 'reading', '[]'::jsonb);

            -- card 事件（reading / ideating / skeleton / error_diagnosis / post_ac / transfer）
            v_card_seq := 0;
            FOR ci IN 1..LEAST(3, 1 + ((i + sidx) % 3)) LOOP
                v_card_seq := v_card_seq + 1;
                v_card_type := (ARRAY['reading','ideating','skeleton',
                                      'error_diagnosis','post_ac','transfer']
                                )[((i + sidx + ci) % 6) + 1];
                INSERT INTO ai_tutor_workflow_event (
                    session_id, run_id, thread_id, event_type,
                    runtime_state, server_event, client_event, failure_bucket,
                    trace_id, event_data, created_at,
                    card_id, card_type, mode_when_produced, referenced_card_ids
                )
                VALUES (
                    v_session_id, v_run_id, v_thread_id, 'card_emitted',
                    NULL, NULL, NULL, NULL, v_trace_id,
                    jsonb_build_object('card_type', v_card_type,
                                       'summary', '演示卡片 ' || v_card_type),
                    NOW() - INTERVAL '1 day' * ((i + sidx) % 6)
                          - INTERVAL '13 minutes' * sidx
                          + INTERVAL '500 ms' * v_card_seq,
                    'demo_card_' || lpad(i::text, 3, '0') || '_'
                                || lpad(sidx::text, 2, '0') || '_'
                                || v_card_seq,
                    v_card_type, 'reading', '[]'::jsonb
                );

                -- 错因诊断后插入一条 AC 提交，让 hit 率非零
                IF v_card_type = 'error_diagnosis' THEN
                    v_diag_time := NOW() - INTERVAL '1 day' * ((i + sidx) % 6)
                                        - INTERVAL '13 minutes' * sidx
                                        + INTERVAL '500 ms' * v_card_seq;
                    v_post_ac_time := v_diag_time + INTERVAL '5 minutes';
                    v_sub_id := 'demo_sub_diag_'
                                || lpad(i::text, 3, '0') || '_'
                                || lpad(sidx::text, 2, '0') || '_'
                                || v_card_seq;
                    INSERT INTO submission (id, problem_id, user_id, username,
                                            code, result, info, language, shared,
                                            statistic_info, ip, create_time)
                    VALUES (v_sub_id, v_problem_id, v_student_id, v_username,
                            '# demo post-diagnosis AC', 0,
                            jsonb_build_object('demo_seed', true, 'after', 'error_diagnosis'),
                            'Python3', FALSE,
                            jsonb_build_object('time_cost', 35, 'memory_cost', 8200),
                            '127.0.0.1', v_post_ac_time)
                    ON CONFLICT (id) DO NOTHING;
                END IF;
            END LOOP;

            -- TASK_COMPLETED / TASK_FAILED 终态事件
            INSERT INTO ai_tutor_workflow_event (
                session_id, run_id, thread_id, event_type,
                runtime_state, server_event, client_event, failure_bucket,
                trace_id, event_data, created_at,
                card_id, card_type, mode_when_produced, referenced_card_ids
            )
            VALUES (
                v_session_id, v_run_id, v_thread_id, 'task_event',
                CASE WHEN (sidx + i) % 9 = 0 THEN 'FAILED' ELSE 'COMPLETED' END,
                CASE WHEN (sidx + i) % 9 = 0 THEN 'TASK_FAILED' ELSE 'TASK_COMPLETED' END,
                NULL,
                CASE WHEN (sidx + i) % 9 = 0
                     THEN (ARRAY['SCHEMA_VIOLATION','SYSTEM_ERROR','TOOL_EXECUTION_FAILED'])[(sidx + i) % 3 + 1]
                     ELSE NULL
                END,
                v_trace_id,
                jsonb_build_object('duration_ms', 1100 + ((i * sidx) % 2000),
                                   'status', CASE WHEN (sidx + i) % 9 = 0 THEN 'FAILED' ELSE 'OK' END,
                                   'metadata', jsonb_build_object('agent', v_phase)),
                NOW() - INTERVAL '1 day' * ((i + sidx) % 6)
                      - INTERVAL '13 minutes' * sidx
                      + INTERVAL '2 seconds',
                NULL, NULL, 'reading', '[]'::jsonb
            );
        END LOOP;
    END LOOP;

    -- ----------------------------------------------------------
    -- 4. quality_trend_score 事件（24 小时趋势）
    -- ----------------------------------------------------------
    FOR v_quality_hour IN 0..23 LOOP
        v_avg_score := 0.62 + ((v_quality_hour % 7) * 0.04)::numeric;
        IF v_avg_score > 0.95 THEN
            v_avg_score := 0.95;
        END IF;
        INSERT INTO ai_tutor_workflow_event (
            session_id, run_id, thread_id, event_type,
            runtime_state, server_event, client_event, failure_bucket,
            trace_id, event_data, created_at,
            card_id, card_type, mode_when_produced, referenced_card_ids
        )
        VALUES (
            'demo_sess_quality_001', 'demo_run_quality_001', 'demo_thread_quality_001',
            'quality_trend_score',
            NULL, NULL, NULL, NULL,
            'demo_trace_quality_001',
            jsonb_build_object('avg_overall_score', v_avg_score,
                               'sample_count', 18 + (v_quality_hour % 5)),
            NOW() - INTERVAL '1 hour' * (24 - v_quality_hour),
            NULL, NULL, 'reading', '[]'::jsonb
        );
    END LOOP;

    -- ----------------------------------------------------------
    -- 5. beta_feedback_report：22 条横跨多种 type/severity/status
    -- ----------------------------------------------------------
    FOR i IN 1..22 LOOP
        SELECT id INTO v_student_id
          FROM "user"
         WHERE username = 'demo_stu_' || lpad((((i - 1) % v_student_count) + 1)::text, 3, '0');

        INSERT INTO beta_feedback_report (
            reporter_user_id, type, severity, description, route,
            problem_id, submission_id, workflow_session_id,
            status, wjx_followup_opened,
            browser_meta, recent_actions,
            mail_status, mail_error, privacy_notice_version,
            created_at, updated_at, resolved_at
        )
        VALUES (
            v_student_id,
            (ARRAY['cant_open','button_dead','page_confusing',
                   'wrong_problem_or_answer','ai_unclear','submit_wrong','other'])[((i - 1) % 7) + 1],
            (ARRAY['blocker','high','medium','low'])[((i - 1) % 4) + 1],
            '[demo-seed] 演示反馈 #' || lpad(i::text, 2, '0')
                                   || '：'
                                   || (ARRAY[
                                       '点击「提交」后没反应',
                                       '页面布局错位看不到测试用例',
                                       'AI 提示和题目对不上',
                                       '编辑器卡顿，输入有延迟',
                                       '语言包课件预览空白',
                                       '骨架代码生成内容含中文乱码',
                                       'WebSocket 频繁掉线'
                                   ])[((i - 1) % 7) + 1],
            (ARRAY['/problems/PPT2-1', '/language-pack-qa', '/classroom/'
                   || v_class_id, '/oj/problem/PPT3-1', '/learn/python'])[((i - 1) % 5) + 1],
            v_problem_ids[((i - 1) % v_problem_count) + 1],
            NULL,
            'demo_sess_' || lpad((((i - 1) % v_student_count) + 1)::text, 3, '0') || '_01',
            (ARRAY['pending','triaging','fixing','resolved','wontfix'])[((i - 1) % 5) + 1],
            ((i % 6) = 0),
            jsonb_build_object('ua', 'Mozilla/5.0 (Windows NT 10.0)',
                               'viewport', '1920x1080',
                               'lang', 'zh-CN',
                               'dpr', 1,
                               'online', true,
                               'network', '4g'),
            jsonb_build_array(
                jsonb_build_object('event_type', 'page_view',
                                   'route', '/oj/problem/PPT2-1',
                                   'created_at', (NOW() - INTERVAL '1 hour' * i)::text),
                jsonb_build_object('event_type', 'feature_click',
                                   'route', '/oj/problem/PPT2-1',
                                   'created_at', (NOW() - INTERVAL '1 hour' * i + INTERVAL '15 seconds')::text)
            ),
            'sent', '', '2026-04-28-v1',
            NOW() - INTERVAL '6 hours' * ((i % 24) + 1),
            NOW() - INTERVAL '1 hour' * (i % 12),
            CASE WHEN ((i - 1) % 5) IN (3, 4)
                 THEN NOW() - INTERVAL '1 hour' * (i % 6)
                 ELSE NULL END
        );
    END LOOP;

    -- ----------------------------------------------------------
    -- 6. 同步班级冗余统计
    -- ----------------------------------------------------------
    UPDATE classroom
       SET problem_count = v_problem_count,
           member_count = v_student_count,
           update_time = NOW()
     WHERE id = v_class_id;

    UPDATE classroom_member cm
       SET problems_solved = COALESCE(sub.solved, 0)
      FROM (
          SELECT s.user_id, COUNT(DISTINCT s.problem_id) AS solved
            FROM submission s
            JOIN classroom_member m ON m.user_id = s.user_id AND m.classroom_id = v_class_id
           WHERE s.result = 0 AND s.id LIKE 'demo_sub_%'
           GROUP BY s.user_id
      ) sub
     WHERE cm.user_id = sub.user_id AND cm.classroom_id = v_class_id;

    RAISE NOTICE 'demo-seed 完成：班级=%, 学生数=%, 题目数=%',
        v_class_id, v_student_count, v_problem_count;
END
$demo$;

COMMIT;

-- ============================================================
-- 校验：必看的几个聚合
-- ============================================================
\echo
\echo == 班级与学生 ==
SELECT c.id, c.name, c.member_count,
       (SELECT COUNT(*) FROM classroom_member WHERE classroom_id = c.id) AS members,
       (SELECT language_pack_id FROM classroom_language_pack WHERE classroom_id = c.id) AS lp_id
  FROM classroom c WHERE c.id = 'demo_python_class_2026';

\echo
\echo == 提交 / AI session / event 计数 ==
SELECT COUNT(*) FILTER (WHERE id LIKE 'demo_sub_%')          AS demo_submissions,
       COUNT(*) FILTER (WHERE id LIKE 'demo_sub_%' AND result = 0) AS demo_ac
  FROM submission;

SELECT COUNT(*) AS demo_sessions
  FROM ai_tutor_workflow_session WHERE session_id LIKE 'demo_sess_%';

SELECT event_type, COUNT(*) AS cnt
  FROM ai_tutor_workflow_event
 WHERE session_id LIKE 'demo_sess_%'
 GROUP BY event_type ORDER BY cnt DESC;

SELECT card_type, COUNT(*) AS cnt
  FROM ai_tutor_workflow_event
 WHERE session_id LIKE 'demo_sess_%' AND card_type IS NOT NULL
 GROUP BY card_type ORDER BY cnt DESC;

\echo
\echo == 反馈 ==
SELECT severity, status, COUNT(*) AS cnt
  FROM beta_feedback_report
 WHERE description LIKE '[demo-seed] %'
 GROUP BY severity, status ORDER BY severity, status;

-- V65：Unified Chat 共享上下文（DeepTutor P3）。
-- 设计依据：docs/plans/2026-04-25-unified-chat-context-design.md §7.2。
--
-- 新增内容：
--   * ai_tutor_workflow_session.active_mode + last_mode_switched_at  - 每个会话的当前 Mode（用户视角）
--   * ai_tutor_workflow_event.card_id / card_type                    - @card: 引用使用的稳定卡片锚点
--   * ai_tutor_workflow_event.mode_when_produced                     - 生成卡片时的用户 Mode
--   * ai_tutor_workflow_event.referenced_card_ids                    - LLM 引用过的 card_id jsonb 数组

ALTER TABLE ai_tutor_workflow_session
    ADD COLUMN IF NOT EXISTS active_mode VARCHAR(32) NOT NULL DEFAULT 'reading',
    ADD COLUMN IF NOT EXISTS last_mode_switched_at TIMESTAMPTZ;

ALTER TABLE ai_tutor_workflow_event
    ADD COLUMN IF NOT EXISTS card_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS card_type VARCHAR(48),
    ADD COLUMN IF NOT EXISTS mode_when_produced VARCHAR(32),
    ADD COLUMN IF NOT EXISTS referenced_card_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 长会话中的 @last_error / @last_visualize 查询也必须命中索引。
CREATE INDEX IF NOT EXISTS idx_atwf_event_card_id
    ON ai_tutor_workflow_event (session_id, card_type, created_at DESC)
    WHERE card_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_atwf_event_card_id_unique
    ON ai_tutor_workflow_event (card_id)
    WHERE card_id IS NOT NULL;

-- V56：删除旧版 ai_workflow_* 表。
-- 工作流运行态已全部迁移到 LangGraph tutor-graph 服务。
-- 投影数据保存在 V55 引入的 ai_tutor_workflow_* 表中。

DROP TABLE IF EXISTS ai_workflow_steering_signal;
DROP TABLE IF EXISTS ai_workflow_plan;
DROP TABLE IF EXISTS ai_workflow_checkpoint;
DROP TABLE IF EXISTS ai_workflow_event;
DROP TABLE IF EXISTS ai_workflow_session;

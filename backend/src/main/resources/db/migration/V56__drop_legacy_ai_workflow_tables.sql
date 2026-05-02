-- V56: Drop legacy ai_workflow_* tables
-- All workflow runtime is now handled by LangGraph tutor-graph service.
-- Projection data lives in ai_tutor_workflow_* tables (V55).

DROP TABLE IF EXISTS ai_workflow_steering_signal;
DROP TABLE IF EXISTS ai_workflow_plan;
DROP TABLE IF EXISTS ai_workflow_checkpoint;
DROP TABLE IF EXISTS ai_workflow_event;
DROP TABLE IF EXISTS ai_workflow_session;

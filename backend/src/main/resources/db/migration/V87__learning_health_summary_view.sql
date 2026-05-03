-- V87__learning_health_summary_view.sql
-- L99 Sprint 05: 学习健康度聚合视图

CREATE OR REPLACE VIEW v_learner_health_summary AS
SELECT
  user_id,
  COUNT(*) FILTER (WHERE result = 0) AS ac_count,
  COUNT(*) AS submit_count,
  ROUND(COUNT(*) FILTER (WHERE result = 0)::NUMERIC / NULLIF(COUNT(*), 0), 4) AS ac_rate,
  COUNT(DISTINCT problem_id) AS problems_attempted,
  COUNT(DISTINCT DATE(create_time)) AS active_days_30d
FROM submission
WHERE create_time >= NOW() - INTERVAL '30 days'
GROUP BY user_id;

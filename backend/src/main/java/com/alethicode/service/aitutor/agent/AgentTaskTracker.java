package com.alethicode.service.aitutor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks TutorAgent task lifecycle per the A2A Task state machine concept.
 * Records are persisted to ai_workflow_event for traceability.
 */
@Component
public class AgentTaskTracker {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskTracker.class);

    private final JdbcTemplate jdbcTemplate;

    public AgentTaskTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records the start of an agent task.
     *
     * @return a tracking context to pass to {@link #complete} or {@link #fail}
     */
    public AgentTaskRecord submit(Long workflowEventId, String agentName, String event) {
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.debug("Agent task submitted: agent={}, event={}, workflowEvent={}, traceId={}",
                agentName, event, workflowEventId, traceId);
        return new AgentTaskRecord(workflowEventId, agentName, event, Instant.now(), traceId);
    }

    public void complete(AgentTaskRecord record, Map<String, Object> artifact) {
        long durationMs = Instant.now().toEpochMilli() - record.startedAt().toEpochMilli();
        log.debug("Agent task completed: agent={}, event={}, duration={}ms",
                record.agentName(), record.event(), durationMs);
        persistStatus(record, AgentTaskStatus.COMPLETED, durationMs);
    }

    public void fail(AgentTaskRecord record, String reason) {
        long durationMs = Instant.now().toEpochMilli() - record.startedAt().toEpochMilli();
        log.warn("Agent task failed: agent={}, event={}, duration={}ms, reason={}",
                record.agentName(), record.event(), durationMs, reason);
        persistStatus(record, AgentTaskStatus.FAILED, durationMs);
    }

    private void persistStatus(AgentTaskRecord record, AgentTaskStatus status, long durationMs) {
        jdbcTemplate.update(
                """
                UPDATE ai_workflow_event
                SET agent_name = ?,
                    agent_status = ?,
                    agent_duration_ms = ?,
                    runtime_state = ?,
                    trace_id = ?
                WHERE id = ?
                """,
                record.agentName(),
                status.name(),
                durationMs,
                status.toRuntimeState().name(),
                record.traceId(),
                record.workflowEventId()
        );
    }

    public record AgentTaskRecord(
            Long workflowEventId,
            String agentName,
            String event,
            Instant startedAt,
            String traceId
    ) {}
}

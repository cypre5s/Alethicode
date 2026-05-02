package com.alethicode.service.aitutor.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.aitutor.profile.LearnerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects agent interaction data for future ML/RL training.
 * Each record captures a complete agent decision cycle:
 * who asked, what was decided, what was produced, and the learner context.
 */
@Service
public class AgentInteractionDataCollector {

    private static final Logger log = LoggerFactory.getLogger(AgentInteractionDataCollector.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AlethicodeProperties properties;
    private final ObjectMapper objectMapper;

    public AgentInteractionDataCollector(AlethicodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Records one agent interaction cycle.
     *
     * @param agentName      which agent handled the event (e.g. DiagnosticsAgent)
     * @param phase          workflow phase (READING, CODING, ERROR_FEEDBACK, etc.)
     * @param event          the triggering event
     * @param userId         learner ID
     * @param problemId      problem ID
     * @param learnerState   learner profile snapshot at decision time
     * @param policyDecision the action policy recommended
     * @param agentOutput    the actual agent output (scaffold, diagnosis, etc.)
     * @param durationMs     how long the agent took
     * @param reactToolCalls tool calls made during ReAct (empty if single-shot)
     */
    public void collect(String agentName,
                        String phase,
                        String event,
                        Long userId,
                        Long problemId,
                        LearnerState learnerState,
                        Map<String, Object> policyDecision,
                        Map<String, Object> agentOutput,
                        long durationMs,
                        List<Map<String, Object>> reactToolCalls) {
        String dataDir = properties.getSystem().getSubmissionDataDir();
        if (dataDir == null || dataDir.isBlank()) {
            return;
        }

        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("record_type", "agent_interaction");
            record.put("agent_name", agentName);
            record.put("phase", phase);
            record.put("event", event);
            record.put("user_id", userId);
            record.put("problem_id", problemId);
            record.put("duration_ms", durationMs);
            record.put("timestamp", Instant.now().toString());

            if (learnerState != null) {
                Map<String, Object> stateSnapshot = new LinkedHashMap<>();
                stateSnapshot.put("calibrated", learnerState.calibrated());
                stateSnapshot.put("frustration_level", learnerState.frustrationLevel());
                stateSnapshot.put("confidence_proxy", learnerState.confidenceProxy());
                stateSnapshot.put("weak_kcs", learnerState.weakKcs());
                stateSnapshot.put("mastery_by_kc", learnerState.masteryByKc());
                record.put("learner_state", stateSnapshot);
            }

            if (policyDecision != null) {
                record.put("policy_decision", policyDecision);
            }

            if (agentOutput != null) {
                record.put("agent_output_keys", agentOutput.keySet().stream().toList());
                String outputType = String.valueOf(agentOutput.getOrDefault("scaffold_type",
                        agentOutput.getOrDefault("root_cause", "")));
                record.put("output_type_hint", abbreviate(outputType, 200));
            }

            if (reactToolCalls != null && !reactToolCalls.isEmpty()) {
                record.put("react_tool_calls", reactToolCalls);
            }

            String date = LocalDate.now(ZoneId.systemDefault()).format(DATE_FMT);
            Path dir = Path.of(dataDir, "agent-interactions");
            Files.createDirectories(dir);
            Path file = dir.resolve(date + ".jsonl");

            String line = objectMapper.writeValueAsString(record) + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.debug("Agent interaction collected: agent={}, user={}, problem={}", agentName, userId, problemId);
        } catch (IOException e) {
            log.warn("Failed to collect agent interaction: agent={}, error={}", agentName, e.getMessage());
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}

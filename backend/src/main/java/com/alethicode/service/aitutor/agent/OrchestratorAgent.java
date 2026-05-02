package com.alethicode.service.aitutor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes (phase, event) to the appropriate TutorAgent(s) and collects output.
 *
 * Supports two dispatch modes:
 * - Single dispatch: first matching agent handles the event (default for most events)
 * - Pipeline dispatch: all matching agents execute in order, outputs are merged
 *
 * Maintains session-level history in nodeOutputs under the "session_history" key,
 * enabling cross-phase context propagation between agents.
 */
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);
    private static final String SESSION_HISTORY_KEY = "session_history";
    private static final int MAX_SESSION_HISTORY_ENTRIES = 20;
    private static final int SUMMARY_MAX_LENGTH = 300;

    private final List<TutorAgent> agents;

    public OrchestratorAgent(List<TutorAgent> agents) {
        this.agents = agents;
    }

    /**
     * Finds the first agent that can handle the event and delegates execution.
     * After execution, appends a summary to session history in nodeOutputs.
     */
    public Map<String, Object> dispatch(AgentContext context) {
        List<Map<String, Object>> sessionHistory = loadSessionHistory(context.nodeOutputs());

        AgentContext enrichedContext = new AgentContext(
                context.event(), context.currentPhase(), context.eventData(),
                context.evidencePack(), context.learnerState(), context.tutorContext(),
                context.problemContext(), context.nodeOutputs(), context.problemId(),
                context.userId(), sessionHistory);

        for (TutorAgent agent : agents) {
            if (agent.canHandle(context.currentPhase(), context.event())) {
                log.debug("Orchestrator dispatching event '{}' to agent '{}'",
                        context.event(), agent.capability().name());
                Map<String, Object> output = agent.execute(enrichedContext);
                appendSessionHistory(context.nodeOutputs(), agent.capability().name(),
                        context.event(), output);
                return output;
            }
        }
        throw new IllegalStateException(
                "No agent registered for phase='" + context.currentPhase() + "', event='" + context.event() + "'");
    }

    /**
     * Dispatches to all matching agents in order and merges their outputs.
     * Later agents see earlier agents' outputs in both nodeOutputs and sessionHistory.
     */
    public Map<String, Object> dispatchPipeline(AgentContext context) {
        List<Map<String, Object>> sessionHistory = loadSessionHistory(context.nodeOutputs());
        List<TutorAgent> matchingAgents = new ArrayList<>();
        for (TutorAgent agent : agents) {
            if (agent.canHandle(context.currentPhase(), context.event())) {
                matchingAgents.add(agent);
            }
        }
        if (matchingAgents.isEmpty()) {
            throw new IllegalStateException(
                    "No agent registered for phase='" + context.currentPhase() + "', event='" + context.event() + "'");
        }

        Map<String, Object> mergedOutput = new LinkedHashMap<>();
        List<Map<String, Object>> runningHistory = new ArrayList<>(sessionHistory);

        for (TutorAgent agent : matchingAgents) {
            AgentContext stepContext = new AgentContext(
                    context.event(), context.currentPhase(), context.eventData(),
                    context.evidencePack(), context.learnerState(), context.tutorContext(),
                    context.problemContext(), context.nodeOutputs(), context.problemId(),
                    context.userId(), runningHistory);

            log.debug("Orchestrator pipeline dispatching event '{}' to agent '{}'",
                    context.event(), agent.capability().name());
            Map<String, Object> agentOutput = agent.execute(stepContext);
            mergedOutput.putAll(agentOutput);

            Map<String, Object> turnEntry = buildSessionHistoryEntry(
                    agent.capability().name(), context.event(), agentOutput);
            runningHistory.add(turnEntry);
        }

        saveSessionHistory(context.nodeOutputs(), runningHistory);
        log.debug("Orchestrator pipeline completed: event='{}', agentCount={}", context.event(), matchingAgents.size());
        return mergedOutput;
    }

    public List<AgentCapability> listCapabilities() {
        return agents.stream().map(TutorAgent::capability).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadSessionHistory(Map<String, Object> nodeOutputs) {
        Object raw = nodeOutputs.get(SESSION_HISTORY_KEY);
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> history = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        entry.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    history.add(entry);
                }
            }
            return history;
        }
        return new ArrayList<>();
    }

    private void appendSessionHistory(Map<String, Object> nodeOutputs,
                                      String agentName, String event,
                                      Map<String, Object> agentOutput) {
        List<Map<String, Object>> history = loadSessionHistory(nodeOutputs);
        history.add(buildSessionHistoryEntry(agentName, event, agentOutput));
        saveSessionHistory(nodeOutputs, history);
    }

    private void saveSessionHistory(Map<String, Object> nodeOutputs, List<Map<String, Object>> history) {
        if (history.size() > MAX_SESSION_HISTORY_ENTRIES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_SESSION_HISTORY_ENTRIES, history.size()));
        }
        if (history.size() > COLLAPSE_THRESHOLD) {
            history = collapseOlderEntries(history);
        }
        nodeOutputs.put(SESSION_HISTORY_KEY, history);
    }

    private static final int COLLAPSE_THRESHOLD = 5;
    private static final int COLLAPSE_KEEP_RECENT = 3;

    /**
     * Folds entries older than the most recent N into a single summary entry,
     * keeping the latest entries intact for agent visibility.
     */
    private List<Map<String, Object>> collapseOlderEntries(List<Map<String, Object>> history) {
        int splitAt = history.size() - COLLAPSE_KEEP_RECENT;
        List<Map<String, Object>> older = history.subList(0, splitAt);
        List<Map<String, Object>> recent = history.subList(splitAt, history.size());

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> entry : older) {
            if ("context_summary".equals(entry.get("agent"))) {
                sb.append(entry.getOrDefault("summary", "")).append(" ");
                continue;
            }
            String agent = String.valueOf(entry.getOrDefault("agent", ""));
            String event = String.valueOf(entry.getOrDefault("event", ""));
            String summary = String.valueOf(entry.getOrDefault("summary", ""));
            if (!summary.isBlank()) {
                sb.append(agent).append("(").append(event).append("): ")
                        .append(abbreviate(summary, 80)).append("; ");
            }
        }

        Map<String, Object> collapsed = new LinkedHashMap<>();
        collapsed.put("agent", "context_summary");
        collapsed.put("event", "COLLAPSED");
        collapsed.put("summary", abbreviate(sb.toString(), 500));
        collapsed.put("collapsed_count", older.size());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(collapsed);
        result.addAll(recent);
        return result;
    }

    private Map<String, Object> buildSessionHistoryEntry(String agentName, String event,
                                                          Map<String, Object> agentOutput) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("agent", agentName);
        entry.put("event", event);
        entry.put("summary", extractOutputSummary(agentOutput));
        entry.put("ts", java.time.Instant.now().toString());
        return entry;
    }

    private String extractOutputSummary(Map<String, Object> output) {
        for (String key : List.of("root_cause", "fix_direction", "reply",
                "self_explanation_prompt", "next_practice_direction",
                "encouragement", "strategy_noticed")) {
            Object val = output.get(key);
            if (val instanceof String s && !s.isBlank()) {
                return abbreviate(s, SUMMARY_MAX_LENGTH);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : output.entrySet()) {
            if (e.getValue() instanceof String s && !s.isBlank()) {
                sb.append(e.getKey()).append("=").append(abbreviate(s, 80)).append("; ");
                if (sb.length() > SUMMARY_MAX_LENGTH) break;
            }
        }
        return abbreviate(sb.toString(), SUMMARY_MAX_LENGTH);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}

package com.alethicode.service.aitutor.evidence;

import java.util.LinkedHashMap;
import java.util.Map;

public record EvidencePack(
        Map<String, Object> problem,
        Map<String, Object> workflow,
        Map<String, Object> submission,
        Map<String, Object> code,
        Map<String, Object> learnerShortTerm,
        Map<String, Object> learnerLongTerm,
        Map<String, Object> courseware,
        Map<String, Object> similarErrors,
        Map<String, Object> kc,
        Map<String, Object> risk,
        Map<String, Object> retrieval,
        Map<String, Object> orchestration,
        Map<String, Object> meta
) {

    public Map<String, Object> contextSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runtime_context", Map.of(
                "session_id", workflow.getOrDefault("session_id", ""),
                "phase", workflow.getOrDefault("phase", ""),
                "event", workflow.getOrDefault("event", "")
        ));
        snapshot.put("session_context", Map.of(
                "pending_human_action", workflow.getOrDefault("pending_human_action", ""),
                "last_event", workflow.getOrDefault("last_event", Map.of())
        ));
        snapshot.put("retrieval_context", Map.of(
                "hit_count", retrieval.getOrDefault("hit_count", 0),
                "sources", retrieval.getOrDefault("sources", java.util.List.of())
        ));
        snapshot.put("learner_memory", Map.of(
                "short_term_keys", learnerShortTerm.keySet(),
                "long_term_keys", learnerLongTerm.keySet()
        ));
        snapshot.put("policy_context", Map.of(
                "risk", risk
        ));
        return snapshot;
    }

    public Map<String, Object> toSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("problem", Map.of(
                "problem_id", problem.get("id"),
                "title", problem.getOrDefault("title", "")
        ));
        summary.put("workflow", Map.of(
                "session_id", workflow.getOrDefault("session_id", ""),
                "phase", workflow.getOrDefault("phase", "")
        ));
        summary.put("retrieval", Map.of(
                "hit_count", retrieval.getOrDefault("hit_count", 0),
                "sources", retrieval.getOrDefault("sources", java.util.List.of())
        ));
        summary.put("similar_errors", Map.of(
                "notebook_hit_count", similarErrors.getOrDefault("notebook_hit_count", 0),
                "memory_hit_count", similarErrors.getOrDefault("memory_hit_count", 0)
        ));
        summary.put("orchestration", orchestration);
        summary.put("risk", risk);
        summary.put("meta", meta);
        return summary;
    }
}

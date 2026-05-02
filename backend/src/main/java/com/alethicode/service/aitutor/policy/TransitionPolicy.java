package com.alethicode.service.aitutor.policy;

import com.alethicode.exception.BusinessExceptions;
import com.alethicode.service.aitutor.contract.PendingHumanAction;
import com.alethicode.service.aitutor.contract.Phase;
import com.alethicode.service.aitutor.contract.WorkflowEvent;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

@Component
public class TransitionPolicy {

    private final Map<Phase, EnumSet<WorkflowEvent>> allowedTransitions = new EnumMap<>(Phase.class);

    public TransitionPolicy() {
        allowedTransitions.put(Phase.READING, EnumSet.of(
                WorkflowEvent.READING,
                WorkflowEvent.IDEATING,
                WorkflowEvent.CODING,
                WorkflowEvent.ERROR_FEEDBACK,
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
        allowedTransitions.put(Phase.IDEATING, EnumSet.of(
                WorkflowEvent.IDEATING,
                WorkflowEvent.CODING,
                WorkflowEvent.ERROR_FEEDBACK,
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
        allowedTransitions.put(Phase.CODING, EnumSet.of(
                WorkflowEvent.CODING,
                WorkflowEvent.ERROR_FEEDBACK,
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
        allowedTransitions.put(Phase.ERROR_FEEDBACK, EnumSet.of(
                WorkflowEvent.ERROR_FEEDBACK,
                WorkflowEvent.READING,
                WorkflowEvent.IDEATING,
                WorkflowEvent.CODING,
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
        allowedTransitions.put(Phase.AC_REVIEW, EnumSet.of(
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.TRANSFER,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
        allowedTransitions.put(Phase.TRANSFER, EnumSet.of(
                WorkflowEvent.TRANSFER,
                WorkflowEvent.CODING,
                WorkflowEvent.ERROR_FEEDBACK,
                WorkflowEvent.AC_REVIEW,
                WorkflowEvent.CHAT,
                WorkflowEvent.AGENT_FEEDBACK,
                WorkflowEvent.KNOWLEDGE_REVIEW
        ));
    }

    public void validateOrThrow(String currentPhaseRaw, String eventRaw, String pendingRaw, Map<String, Object> eventData) {
        Phase currentPhase = Phase.from(currentPhaseRaw)
                .orElseThrow(() -> BusinessExceptions.fromLegacy("error", "Illegal workflow transition: " + currentPhaseRaw + " -> " + eventRaw));
        WorkflowEvent event = WorkflowEvent.from(eventRaw)
                .orElseThrow(() -> BusinessExceptions.fromLegacy("error", "Illegal workflow transition: " + currentPhaseRaw + " -> " + eventRaw));
        EnumSet<WorkflowEvent> allowed = allowedTransitions.getOrDefault(currentPhase, EnumSet.noneOf(WorkflowEvent.class));
        if (!allowed.contains(event)) {
            throw BusinessExceptions.fromLegacy("error", "Illegal workflow transition: " + currentPhase.name() + " -> " + event.name());
        }

        PendingHumanAction pending = PendingHumanAction.from(pendingRaw).orElse(PendingHumanAction.NONE);
        if (pending == PendingHumanAction.CONFIRM_TRANSFER && event == WorkflowEvent.CODING) {
            throw BusinessExceptions.fromLegacy("error", "Illegal workflow transition: " + currentPhase.name() + " -> " + event.name());
        }
    }

    public void validateCheckpointRestoreOrThrow(String currentPhaseRaw, String restoredPhaseRaw, String pendingRaw) {
        Phase currentPhase = Phase.from(currentPhaseRaw)
                .orElseThrow(() -> BusinessExceptions.fromLegacy("error", "Illegal workflow checkpoint restore: " + currentPhaseRaw + " -> " + restoredPhaseRaw));
        Optional<Phase> restoredPhase = Phase.from(restoredPhaseRaw);
        if (restoredPhase.isEmpty()) {
            throw BusinessExceptions.fromLegacy("error", "Illegal workflow checkpoint restore: " + currentPhase.name() + " -> " + restoredPhaseRaw);
        }
        if (PendingHumanAction.from(pendingRaw).isEmpty() && pendingRaw != null && !pendingRaw.isBlank()) {
            throw BusinessExceptions.fromLegacy("error", "Illegal workflow checkpoint restore: " + currentPhase.name() + " -> " + restoredPhase.get().name());
        }
    }
}

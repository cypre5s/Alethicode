package com.alethicode.service.aitutor.contract;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeContract(
        String sessionId,
        String taskId,
        String checkpointId,
        String traceId,
        RuntimeState runtimeState,
        String clientEvent,
        ServerEvent serverEvent,
        String approvalState,
        FailureBucket failureBucket,
        Instant timestamp
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", sessionId);
        map.put("task_id", taskId);
        map.put("checkpoint_id", checkpointId);
        map.put("trace_id", traceId);
        map.put("runtime_state", runtimeState == null ? null : runtimeState.name());
        map.put("client_event", clientEvent);
        map.put("server_event", serverEvent == null ? null : serverEvent.name());
        map.put("approval_state", approvalState);
        map.put("failure_bucket", failureBucket == null ? null : failureBucket.name());
        map.put("timestamp", timestamp == null ? null : timestamp.toString());
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sessionId;
        private String taskId;
        private String checkpointId;
        private String traceId;
        private RuntimeState runtimeState;
        private String clientEvent;
        private ServerEvent serverEvent;
        private String approvalState;
        private FailureBucket failureBucket;
        private Instant timestamp;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder checkpointId(String checkpointId) { this.checkpointId = checkpointId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder runtimeState(RuntimeState runtimeState) { this.runtimeState = runtimeState; return this; }
        public Builder clientEvent(String clientEvent) { this.clientEvent = clientEvent; return this; }
        public Builder serverEvent(ServerEvent serverEvent) { this.serverEvent = serverEvent; return this; }
        public Builder approvalState(String approvalState) { this.approvalState = approvalState; return this; }
        public Builder failureBucket(FailureBucket failureBucket) { this.failureBucket = failureBucket; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RuntimeContract build() {
            return new RuntimeContract(sessionId, taskId, checkpointId, traceId,
                    runtimeState, clientEvent, serverEvent, approvalState, failureBucket,
                    timestamp == null ? Instant.now() : timestamp);
        }
    }
}

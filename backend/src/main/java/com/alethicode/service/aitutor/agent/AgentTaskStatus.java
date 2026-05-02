package com.alethicode.service.aitutor.agent;

import com.alethicode.service.aitutor.contract.RuntimeState;

/**
 * Task lifecycle states aligned with A2A Task state machine.
 */
public enum AgentTaskStatus {
    SUBMITTED,
    WORKING,
    COMPLETED,
    FAILED;

    public RuntimeState toRuntimeState() {
        return switch (this) {
            case SUBMITTED -> RuntimeState.QUEUED;
            case WORKING -> RuntimeState.RUNNING;
            case COMPLETED -> RuntimeState.COMPLETED;
            case FAILED -> RuntimeState.FAILED;
        };
    }

    public static AgentTaskStatus fromRuntimeState(RuntimeState state) {
        return switch (state) {
            case QUEUED -> SUBMITTED;
            case RUNNING, WAITING_TOOL, RESTORING -> WORKING;
            case COMPLETED -> COMPLETED;
            case FAILED, EXPIRED, INTERRUPTED, WAITING_HUMAN_APPROVAL -> FAILED;
        };
    }
}

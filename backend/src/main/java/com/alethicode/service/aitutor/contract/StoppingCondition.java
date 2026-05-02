package com.alethicode.service.aitutor.contract;

public record StoppingCondition(
        int maxIterations,
        int maxRepeatToolCalls,
        int maxCriticFails,
        int timeoutSeconds
) {

    public static StoppingCondition defaults() {
        return new StoppingCondition(5, 3, 3, 300);
    }

    public boolean iterationExceeded(int current) {
        return current > maxIterations;
    }

    public boolean repeatToolCallExceeded(int count) {
        return count > maxRepeatToolCalls;
    }

    public boolean criticFailExceeded(int count) {
        return count > maxCriticFails;
    }
}

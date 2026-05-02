package com.alethicode.service.aitutor.agent;

import java.util.Map;

/**
 * Interface for modular tutor agents that handle specific workflow events.
 * Replaces the monolithic switch-case in applyPhaseOutput.
 */
public interface TutorAgent {

    /**
     * @return self-describing capability card for routing and traceability
     */
    AgentCapability capability();

    /**
     * @return true if this agent can handle the given (phase, event) combination
     */
    boolean canHandle(String phase, String event);

    /**
     * Executes the agent logic and returns a payload to be placed in nodeOutputs.
     *
     * @param context all contextual information needed for execution
     * @return output payload (will be placed under the appropriate key in nodeOutputs)
     */
    Map<String, Object> execute(AgentContext context);
}

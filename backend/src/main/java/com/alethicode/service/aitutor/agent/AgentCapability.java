package com.alethicode.service.aitutor.agent;

import java.util.List;

/**
 * Self-description of a TutorAgent's capabilities.
 * Mirrors A2A AgentCard concept for internal routing and traceability.
 *
 * @param name           stable agent identifier
 * @param description    human-readable description
 * @param supportedEvents workflow events this agent can handle
 * @param supportedPhases phases during which this agent is active
 */
public record AgentCapability(
        String name,
        String description,
        List<String> supportedEvents,
        List<String> supportedPhases
) {}

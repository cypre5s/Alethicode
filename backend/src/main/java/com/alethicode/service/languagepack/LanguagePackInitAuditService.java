package com.alethicode.service.languagepack;

public interface LanguagePackInitAuditService {

    Long startAgentRun(Long taskId, String agentName, String sourceStage, String promptVersion, String inputArtifactHash);

    void completeAgentRun(Long runId, String outputArtifactHash);

    void failAgentRun(Long runId, String failureReason);

    String replaceJsonArtifact(Long taskId, String artifactType, String sourceStage, String contentJson);

    String replaceMarkdownArtifact(Long taskId, String artifactType, String sourceStage, String contentMarkdown);
}

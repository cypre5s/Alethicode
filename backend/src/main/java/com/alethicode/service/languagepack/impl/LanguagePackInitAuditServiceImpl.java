package com.alethicode.service.languagepack.impl;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.languagepack.LanguagePackInitAuditService;
import com.alethicode.util.HashUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LanguagePackInitAuditServiceImpl implements LanguagePackInitAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;

    public LanguagePackInitAuditServiceImpl(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
    }

    @Override
    public Long startAgentRun(Long taskId, String agentName, String sourceStage, String promptVersion, String inputArtifactHash) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO language_pack_init_agent_run(
                    task_id, agent_name, source_stage, model_name, prompt_version,
                    input_artifact_hash, status, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, 'running', now(), now())
                RETURNING id
                """,
                Long.class,
                taskId,
                agentName,
                sourceStage,
                resolveModelName(),
                promptVersion,
                safeHash(inputArtifactHash)
        );
    }

    @Override
    public void completeAgentRun(Long runId, String outputArtifactHash) {
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_agent_run
                SET status = 'completed',
                    output_artifact_hash = ?,
                    failure_reason = null,
                    update_time = now()
                WHERE id = ?
                """,
                safeHash(outputArtifactHash),
                runId
        );
    }

    @Override
    public void failAgentRun(Long runId, String failureReason) {
        jdbcTemplate.update(
                """
                UPDATE language_pack_init_agent_run
                SET status = 'failed',
                    failure_reason = ?,
                    update_time = now()
                WHERE id = ?
                """,
                failureReason == null ? "" : failureReason,
                runId
        );
    }

    @Override
    public String replaceJsonArtifact(Long taskId, String artifactType, String sourceStage, String contentJson) {
        String normalized = contentJson == null || contentJson.isBlank() ? "{}" : contentJson;
        String hash = HashUtils.sha256(normalized);
        deleteArtifact(taskId, artifactType);
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(
                    task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time
                ) VALUES (?, ?, ?, ?, '', ?, now())
                """,
                taskId,
                artifactType,
                sourceStage,
                normalized,
                hash
        );
        return hash;
    }

    @Override
    public String replaceMarkdownArtifact(Long taskId, String artifactType, String sourceStage, String contentMarkdown) {
        String normalized = contentMarkdown == null ? "" : contentMarkdown;
        String hash = HashUtils.sha256(normalized);
        deleteArtifact(taskId, artifactType);
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_artifact(
                    task_id, artifact_type, source_stage, content_json, content_markdown, content_hash, create_time
                ) VALUES (?, ?, ?, '{}', ?, ?, now())
                """,
                taskId,
                artifactType,
                sourceStage,
                normalized,
                hash
        );
        return hash;
    }

    private void deleteArtifact(Long taskId, String artifactType) {
        jdbcTemplate.update(
                "DELETE FROM language_pack_init_artifact WHERE task_id = ? AND artifact_type = ?",
                taskId,
                artifactType
        );
    }

    private String safeHash(String raw) {
        return HashUtils.sha256(raw == null ? "" : raw);
    }

    private String resolveModelName() {
        String initModel = aiModelGateway.readConfigOrDefault("INIT_LLM_MODEL", null);
        String modelName = initModel != null ? initModel : aiModelGateway.readConfigOrDefault("LLM_MODEL", "MiniMax-M2.7");
        if (modelName == null || modelName.isBlank()) {
            return "MiniMax-M2.7";
        }
        return modelName.strip();
    }
}

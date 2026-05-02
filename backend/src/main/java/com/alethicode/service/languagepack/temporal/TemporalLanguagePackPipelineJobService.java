package com.alethicode.service.languagepack.temporal;

import com.alethicode.config.TemporalLanguagePackWorkflowConfig.LanguagePackTemporalWorkerLauncher;
import com.alethicode.dto.response.LanguagePackPipelineJobResponse;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackPipelineJobService;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "alethicode.temporal.enabled", havingValue = "true")
public class TemporalLanguagePackPipelineJobService implements LanguagePackPipelineJobService {

    private final WorkflowClient workflowClient;
    private final JdbcTemplate jdbcTemplate;
    private final String taskQueue;
    private final LanguagePackTemporalWorkerLauncher workerLauncher;

    public TemporalLanguagePackPipelineJobService(
            WorkflowClient workflowClient,
            JdbcTemplate jdbcTemplate,
            @Value("${alethicode.temporal.task-queue:language-pack-pipeline}") String taskQueue,
            LanguagePackTemporalWorkerLauncher workerLauncher
    ) {
        this.workflowClient = workflowClient;
        this.jdbcTemplate = jdbcTemplate;
        this.taskQueue = taskQueue == null || taskQueue.isBlank() ? "language-pack-pipeline" : taskQueue.strip();
        this.workerLauncher = workerLauncher;
    }

    @Override
    public LanguagePackPipelineJobResponse startJob(Long taskId) {
        requireWorkerReady();
        ensureTaskExists(taskId);
        String jobId = buildJobId(taskId);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(jobId)
                .setWorkflowExecutionTimeout(Duration.ofHours(6))
                .build();
        LanguagePackPipelineWorkflow workflow = workflowClient.newWorkflowStub(LanguagePackPipelineWorkflow.class, options);
        WorkflowExecution execution = WorkflowClient.start(workflow::run, new LanguagePackPipelineRequest(taskId));
        return describe(taskId, jobId, execution.getRunId());
    }

    @Override
    public LanguagePackPipelineJobResponse getJob(Long taskId, String jobId) {
        requireWorkerReady();
        ensureTaskExists(taskId);
        assertJobBelongsToTask(taskId, jobId);
        return describe(taskId, jobId, null);
    }

    @Override
    public LanguagePackPipelineJobResponse cancelJob(Long taskId, String jobId) {
        requireWorkerReady();
        ensureTaskExists(taskId);
        assertJobBelongsToTask(taskId, jobId);
        try {
            workflowClient.newUntypedWorkflowStub(jobId).cancel();
            return describe(taskId, jobId, null);
        } catch (WorkflowNotFoundException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pipeline job not found", exception);
        }
    }

    @Override
    public LanguagePackPipelineJobResponse retryJob(Long taskId, String jobId) {
        requireWorkerReady();
        ensureTaskExists(taskId);
        assertJobBelongsToTask(taskId, jobId);
        return startJob(taskId);
    }

    private void requireWorkerReady() {
        if (!workerLauncher.isWorkerRunning()) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "TEMPORAL_NOT_RUNNING: Temporal 容器未启动。请管理员 SSH 到云主机执行 bash scripts/temporal-on.sh 后等待 60 秒重试。"
            );
        }
    }

    private LanguagePackPipelineJobResponse describe(Long taskId, String jobId, String runIdHint) {
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(jobId);
            WorkflowExecutionDescription description = stub.describe();
            String runId = runIdHint == null || runIdHint.isBlank()
                    ? description.getExecution().getRunId()
                    : runIdHint;
            return new LanguagePackPipelineJobResponse(
                    jobId,
                    taskId,
                    description.getExecution().getWorkflowId(),
                    runId,
                    description.getStatus().name().toLowerCase(Locale.ROOT),
                    loadCurrentStep(taskId),
                    description.getStartTime(),
                    description.getCloseTime()
            );
        } catch (WorkflowNotFoundException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Pipeline job not found", exception);
        }
    }

    private void ensureTaskExists(Long taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM language_pack_init_task WHERE id = ?",
                Integer.class,
                taskId
        );
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
    }

    private void assertJobBelongsToTask(Long taskId, String jobId) {
        String prefix = "language-pack-init-" + taskId + "-";
        if (jobId == null || !jobId.startsWith(prefix)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Pipeline job does not belong to this init task");
        }
    }

    private String buildJobId(Long taskId) {
        return "language-pack-init-" + taskId + "-" + UUID.randomUUID();
    }

    private String loadCurrentStep(Long taskId) {
        return jdbcTemplate.query(
                """
                SELECT active_step_key, stage
                FROM language_pack_init_task
                WHERE id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    String activeStep = rs.getString("active_step_key");
                    if (activeStep != null && !activeStep.isBlank()) {
                        return activeStep;
                    }
                    return rs.getString("stage");
                },
                taskId
        );
    }
}

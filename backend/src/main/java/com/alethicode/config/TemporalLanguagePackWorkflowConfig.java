package com.alethicode.config;

import com.alethicode.service.languagepack.temporal.LanguagePackPipelineActivities;
import com.alethicode.service.languagepack.temporal.LanguagePackPipelineWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Temporal 集成配置。
 *
 * 2C4G 容量优化重构（2026-04-30）：
 *   - WorkflowServiceStubs 改 lazy 模式：bean 创建时不强制建立 gRPC 长连接，
 *     避免 backend 启动时被 Temporal 容器缺席阻塞或失败。
 *   - WorkerFactory 不在 bean 初始化阶段直接 start，改由 LanguagePackTemporalWorkerLauncher
 *     在 ApplicationReadyEvent 之后由后台 scheduler 每 60 秒重试一次健康探测，
 *     发现 Temporal 在线后才注册 worker。Temporal 缺席时只记 debug 日志，不抛异常。
 *   - 上层 service 通过 LanguagePackTemporalWorkerLauncher#isWorkerRunning 判断
 *     当前是否可调度 workflow；不可调度时返回 503 TEMPORAL_NOT_RUNNING。
 *
 * 这样：日常关闭 Temporal 容器节省 ~512MB；管理员 SSH 跑 scripts/temporal-on.sh
 * 启动后 backend 在 60 秒内自动注册 worker，无需重启 backend。
 */
@Configuration
@ConditionalOnProperty(name = "alethicode.temporal.enabled", havingValue = "true")
public class TemporalLanguagePackWorkflowConfig {

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(@Value("${alethicode.temporal.target:}") String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("alethicode.temporal.target is required when Temporal is enabled");
        }
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target.strip())
                .setRpcTimeout(Duration.ofSeconds(5))
                .build();
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    WorkflowClient workflowClient(
            WorkflowServiceStubs workflowServiceStubs,
            @Value("${alethicode.temporal.namespace:default}") String namespace
    ) {
        return WorkflowClient.newInstance(
                workflowServiceStubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace == null || namespace.isBlank() ? "default" : namespace.strip())
                        .build()
        );
    }

    @Bean
    LanguagePackTemporalWorkerLauncher languagePackTemporalWorkerLauncher(
            WorkflowClient workflowClient,
            LanguagePackPipelineActivities activities,
            @Value("${alethicode.temporal.task-queue:language-pack-pipeline}") String taskQueue
    ) {
        String normalizedTaskQueue = taskQueue == null || taskQueue.isBlank()
                ? "language-pack-pipeline"
                : taskQueue.strip();
        return new LanguagePackTemporalWorkerLauncher(workflowClient, activities, normalizedTaskQueue);
    }

    /**
     * 后台异步轮询：每 60 秒尝试一次连 Temporal、注册 worker。
     * Temporal 不可达时只记 debug 日志，不抛异常。
     * 一旦 Temporal 启动就自动注册 worker，无需重启 backend。
     */
    public static class LanguagePackTemporalWorkerLauncher {
        private static final Logger log = LoggerFactory.getLogger(LanguagePackTemporalWorkerLauncher.class);
        private static final long RETRY_INTERVAL_SECONDS = 60L;

        private final WorkflowClient client;
        private final LanguagePackPipelineActivities activities;
        private final String taskQueue;
        private final AtomicReference<WorkerFactory> factoryRef = new AtomicReference<>();
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "temporal-worker-launcher");
            t.setDaemon(true);
            return t;
        });

        LanguagePackTemporalWorkerLauncher(
                WorkflowClient client,
                LanguagePackPipelineActivities activities,
                String taskQueue
        ) {
            this.client = client;
            this.activities = activities;
            this.taskQueue = taskQueue;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onApplicationReady() {
            scheduler.scheduleWithFixedDelay(this::tryStart, 0L, RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        private void tryStart() {
            if (factoryRef.get() != null) {
                return;
            }
            try {
                client.getWorkflowServiceStubs().healthCheck();

                WorkerFactory factory = WorkerFactory.newInstance(client);
                Worker worker = factory.newWorker(taskQueue);
                worker.registerWorkflowImplementationTypes(LanguagePackPipelineWorkflowImpl.class);
                worker.registerActivitiesImplementations(activities);
                factory.start();

                if (factoryRef.compareAndSet(null, factory)) {
                    log.info("Temporal worker started on task queue '{}'", taskQueue);
                } else {
                    factory.shutdown();
                }
            } catch (Throwable t) {
                if (log.isDebugEnabled()) {
                    log.debug("Temporal not yet reachable, will retry in {}s: {}", RETRY_INTERVAL_SECONDS, t.getMessage());
                }
            }
        }

        public boolean isWorkerRunning() {
            return factoryRef.get() != null;
        }

        @PreDestroy
        public void shutdown() {
            scheduler.shutdownNow();
            WorkerFactory f = factoryRef.getAndSet(null);
            if (f != null) {
                f.shutdown();
            }
        }
    }
}

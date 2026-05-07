package com.alethicode.service.aitutor.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Python {@code tutor_graph} 服务的 HTTP 客户端。
 *
 * <p>底层使用 JDK {@link HttpClient} 并设置连接超时，避免 tutor-graph 慢响应或崩溃时耗尽
 * Java 后端文件描述符。单次响应截止时间由调用方的 {@code .block(Duration)} 控制。</p>
 *
 * <p>项目是 servlet 栈，避免为单个内部客户端额外引入 {@code reactor-netty} 事件循环。</p>
 */
@Component
public class TutorGraphClient {

    private static final Logger log = LoggerFactory.getLogger(TutorGraphClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TutorGraphClient(
            @Value("${alethicode.tutor-graph.base-url:http://127.0.0.1:8100}") String baseUrl,
            @Value("${alethicode.internal.service-key:}") String serviceKey
    ) {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Key", serviceKey)
                .clientConnector(new JdkClientHttpConnector(jdkHttpClient))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @CircuitBreaker(name = "tutorGraph")
    @Retry(name = "tutorGraph")
    public Mono<Map<String, Object>> createThread(String sessionId, long userId, long problemId, String language) {
        return createThread(sessionId, userId, problemId, language, null);
    }

    public Mono<Map<String, Object>> createThread(String sessionId, long userId, long problemId, String language,
                                                  Map<String, Object> context) {
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("session_id", sessionId);
        body.put("user_id", userId);
        body.put("problem_id", problemId);
        body.put("language", language);
        if (context != null && !context.isEmpty()) {
            body.put("context", context);
        }
        return webClient.post()
                .uri("/internal/graph/threads")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    @Retry(name = "tutorGraph")
    public Mono<Map<String, Object>> createRun(
            String sessionId, String threadId, long userId, long problemId,
            String language, String event, Map<String, Object> eventData
    ) {
        Map<String, Object> body = Map.of(
                "session_id", sessionId,
                "thread_id", threadId,
                "user_id", userId,
                "problem_id", problemId,
                "language", language,
                "event", event,
                "event_data", eventData != null ? eventData : Map.of()
        );
        return webClient.post()
                .uri("/internal/graph/runs")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    public Mono<Map<String, Object>> getThreadState(String threadId) {
        return webClient.get()
                .uri("/internal/graph/threads/{threadId}/state", threadId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    public Mono<Map<String, Object>> getRunEvents(String runId) {
        return webClient.get()
                .uri("/internal/graph/runs/{runId}/events", runId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    public Mono<Map<String, Object>> cancelRun(String runId) {
        return webClient.post()
                .uri("/internal/graph/runs/{runId}/cancel", runId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    @Retry(name = "tutorGraph")
    public Mono<Map<String, Object>> listCheckpoints(String threadId, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/graph/threads/{threadId}/checkpoints")
                        .queryParam("limit", limit)
                        .build(threadId))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    public Mono<Map<String, Object>> restoreCheckpoint(String threadId, String checkpointId) {
        return webClient.post()
                .uri("/internal/graph/threads/{threadId}/restore", threadId)
                .bodyValue(Map.of("checkpoint_id", checkpointId))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    @CircuitBreaker(name = "tutorGraph")
    public Mono<Map<String, Object>> resumeRun(String runId, String action, Map<String, Object> data) {
        return webClient.post()
                .uri("/internal/graph/runs/{runId}/resume", runId)
                .bodyValue(Map.of("action", action, "data", data != null ? data : Map.of()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }

    public Mono<Map<String, Object>> health() {
        return webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m);
    }
}

package com.alethicode.service.rag;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.rag.dto.RagCoursewareQueryRequest;
import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAcceptedResponse;
import com.alethicode.service.rag.dto.RagIndexRequest;
import com.alethicode.service.rag.dto.RagMemoryQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.alethicode.service.rag.dto.RagSimilarErrorQueryRequest;
import com.alethicode.service.rag.dto.RagTransferQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * WebClient-backed implementation. Mirrors {@code TutorGraphClient}'s
 * connector setup so we have a single place that decides socket-level
 * behaviour for outbound internal HTTP traffic.
 *
 * <p>All calls are synchronous (use {@code .block(...)}). The RAG service
 * is in the trust boundary; failures bubble up as {@link RagServiceException}
 * so callers (worker / retrieval services) can decide between retry,
 * fail-fast, or degrade.
 */
@Component
public class HttpRagServiceClient implements RagServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRagServiceClient.class);

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    private final Duration queryTimeout;
    private final Duration indexTimeout;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpRagServiceClient(AlethicodeProperties properties, MeterRegistry meterRegistry) {
        this(properties.getRag(), meterRegistry);
    }

    HttpRagServiceClient(AlethicodeProperties.Rag ragProperties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.queryTimeout = Duration.ofSeconds(ragProperties.getQueryTimeoutSeconds());
        this.indexTimeout = Duration.ofSeconds(ragProperties.getIndexTimeoutSeconds());
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ragProperties.getConnectTimeoutSeconds()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        // The alethicode-rag wire format is snake_case. Spring Boot's global Jackson
        // builder applies SNAKE_CASE via JacksonConfig, but WebClient builds its own
        // codecs that bypass that customizer — every record we ship would end up
        // emitting camelCase ("languagePackId" instead of "language_pack_id"),
        // failing the FastAPI Pydantic schema validation. Force the codec to use
        // the same naming strategy explicitly.
        ObjectMapper snakeMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(snakeMapper, MediaType.APPLICATION_JSON));
                    c.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(snakeMapper, MediaType.APPLICATION_JSON));
                })
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(ragProperties.getBaseUrl())
                .defaultHeader("X-Internal-Token", ragProperties.getInternalToken())
                .clientConnector(new JdkClientHttpConnector(jdkHttpClient))
                .exchangeStrategies(strategies)
                .build();
        log.info("HttpRagServiceClient configured: baseUrl={} queryTimeout={}s indexTimeout={}s",
                ragProperties.getBaseUrl(),
                ragProperties.getQueryTimeoutSeconds(),
                ragProperties.getIndexTimeoutSeconds());
    }

    @Override
    @CircuitBreaker(name = "ragQuery")
    public RagQueryHits queryCourseware(RagCoursewareQueryRequest request) {
        return query("/v1/rag/query/courseware", request);
    }

    @Override
    @CircuitBreaker(name = "ragQuery")
    public RagQueryHits querySimilarError(RagSimilarErrorQueryRequest request) {
        return query("/v1/rag/query/similar-error", request);
    }

    @Override
    @CircuitBreaker(name = "ragQuery")
    public RagQueryHits queryMemory(RagMemoryQueryRequest request) {
        return query("/v1/rag/query/memory", request);
    }

    @Override
    @CircuitBreaker(name = "ragQuery")
    public RagQueryHits queryTransfer(RagTransferQueryRequest request) {
        return query("/v1/rag/query/transfer", request);
    }

    @Override
    public RagIndexAcceptedResponse indexNow(
            RagEntityType entityType,
            String entityId,
            String content,
            Map<String, Object> metadata
    ) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        RagIndexRequest body = new RagIndexRequest(entityId, content, metadata);
        try {
            return webClient.post()
                    .uri("/v1/rag/index/{entity_type}", entityType.slug())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RagIndexAcceptedResponse.class)
                    .block(indexTimeout);
        } catch (WebClientResponseException ex) {
            throw mapResponseError(ex, "indexNow", entityType.slug() + ":" + entityId);
        } catch (RuntimeException ex) {
            throw new RagServiceException(
                    "indexNow transport failure for " + entityType.slug() + ":" + entityId,
                    ex
            );
        }
    }

    @Override
    public void deleteNow(RagEntityType entityType, String entityId) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId is required");
        }
        try {
            webClient.delete()
                    .uri("/v1/rag/index/{entity_type}/{entity_id}", entityType.slug(), entityId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(indexTimeout);
        } catch (WebClientResponseException ex) {
            // 404 on delete is not an error: the row was never indexed.
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return;
            }
            throw mapResponseError(ex, "deleteNow", entityType.slug() + ":" + entityId);
        } catch (RuntimeException ex) {
            throw new RagServiceException(
                    "deleteNow transport failure for " + entityType.slug() + ":" + entityId,
                    ex
            );
        }
    }

    @Override
    public void wakeUpPipeline() {
        try {
            webClient.post()
                    .uri("/v1/rag/index/pipeline/drain")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
            log.debug("wakeUpPipeline: drain triggered");
        } catch (Exception ex) {
            log.warn("wakeUpPipeline failed (will fall back to 30s outbox worker): {}",
                     ex.getMessage());
        }
    }

    private RagQueryHits query(String uri, Object body) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String endpoint = endpointTag(uri);
        try {
            RagQueryHits hits = webClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RagQueryHits.class)
                    .block(queryTimeout);
            sample.stop(meterRegistry.timer(
                    "rag_query_latency_seconds", "endpoint", endpoint, "outcome", "success"));
            return hits == null ? new RagQueryHits(null, null, null, null) : hits;
        } catch (WebClientResponseException ex) {
            sample.stop(meterRegistry.timer(
                    "rag_query_latency_seconds", "endpoint", endpoint, "outcome", "http_error"));
            throw mapResponseError(ex, "query", uri);
        } catch (RuntimeException ex) {
            sample.stop(meterRegistry.timer(
                    "rag_query_latency_seconds", "endpoint", endpoint, "outcome", "transport_error"));
            throw new RagServiceException("query transport failure for " + uri, ex);
        }
    }

    private static String endpointTag(String uri) {
        // /v1/rag/query/courseware → "courseware"
        int slash = uri.lastIndexOf('/');
        return slash >= 0 && slash + 1 < uri.length() ? uri.substring(slash + 1) : uri;
    }

    private RagServiceException mapResponseError(WebClientResponseException ex, String op, String key) {
        String body = ex.getResponseBodyAsString();
        String summary = "%s %s -> %s; body=%s".formatted(
                op,
                key,
                ex.getStatusCode(),
                body == null || body.isBlank() ? "(empty)" : body.substring(0, Math.min(body.length(), 500))
        );
        log.warn("alethicode-rag {}", summary);
        return new RagServiceException(summary, ex.getStatusCode().value(), ex);
    }
}

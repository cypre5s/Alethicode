package com.alethicode.service.rag;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.service.rag.dto.RagCoursewareQueryRequest;
import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAcceptedResponse;
import com.alethicode.service.rag.dto.RagMemoryQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.alethicode.service.rag.dto.RagSimilarErrorQueryRequest;
import com.alethicode.service.rag.dto.RagTransferQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用 JDK 自带 {@link HttpServer} 捕获发往 alethicode-rag 的请求形态。
 *
 * <p>无需引入 WireMock；JDK server 已足够断言 HTTP body，并减少测试依赖。</p>
 */
class HttpRagServiceClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private final AtomicReference<CannedResponse> canned = new AtomicReference<>(
            new CannedResponse(200, "{}")
    );
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        captured.set(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                new String(bytes, StandardCharsets.UTF_8)
        ));
        CannedResponse resp = canned.get();
        byte[] body = resp.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(resp.status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpRagServiceClient client(String token) {
        AlethicodeProperties.Rag ragProperties = new AlethicodeProperties.Rag();
        ragProperties.setBaseUrl("http://127.0.0.1:" + port);
        ragProperties.setInternalToken(token);
        ragProperties.setQueryTimeoutSeconds(5);
        ragProperties.setConnectTimeoutSeconds(2);
        ragProperties.setIndexTimeoutSeconds(5);
        return new HttpRagServiceClient(
                ragProperties,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
    }

    @Test
    void coursewareQueryPostsExpectedShapeAndDeserializesHits() throws Exception {
        canned.set(new CannedResponse(200, """
                {
                  "entities": [{"entity_id": "Enumerate", "entity_type": "method", "description": "loop helper"}],
                  "relations": [],
                  "chunks": [{"chunk_id": "c1", "content": "demo", "score": 0.91}],
                  "raw_context": "ctx-blob"
                }
                """));

        RagQueryHits hits = client("dev-token").queryCourseware(
                new RagCoursewareQueryRequest(43L, "for 循环", List.of("loop"), 5)
        );

        CapturedRequest req = captured.get();
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.path).isEqualTo("/v1/rag/query/courseware");
        assertThat(req.token).isEqualTo("dev-token");
        Map<String, Object> body = mapper.readValue(req.body, Map.class);
        assertThat(body).containsEntry("language_pack_id", 43);
        assertThat(body).containsEntry("query", "for 循环");
        assertThat(body).containsEntry("top_k", 5);
        assertThat((List<?>) body.get("kc_ids")).isEqualTo(List.of("loop"));

        assertThat(hits.entities()).hasSize(1);
        assertThat(hits.entities().get(0).entityId()).isEqualTo("Enumerate");
        assertThat(hits.chunks().get(0).score()).isEqualTo(0.91);
        assertThat(hits.rawContext()).isEqualTo("ctx-blob");
    }

    @Test
    void similarErrorQueryRoutesToCorrectPath() {
        canned.set(new CannedResponse(200, "{}"));
        client("k").querySimilarError(
                new RagSimilarErrorQueryRequest(7L, 99L, "IndexError", "list 越界", 3)
        );
        assertThat(captured.get().path).isEqualTo("/v1/rag/query/similar-error");
    }

    @Test
    void memoryQueryRoutesToCorrectPath() {
        canned.set(new CannedResponse(200, "{}"));
        client("k").queryMemory(
                new RagMemoryQueryRequest(7L, List.of("loop"), null, "学生最近错的", 3)
        );
        assertThat(captured.get().path).isEqualTo("/v1/rag/query/memory");
    }

    @Test
    void transferQueryRoutesToCorrectPath() {
        canned.set(new CannedResponse(200, "{}"));
        client("k").queryTransfer(
                new RagTransferQueryRequest(99L, List.of(), "类似题目", 3)
        );
        assertThat(captured.get().path).isEqualTo("/v1/rag/query/transfer");
    }

    @Test
    void indexNowSendsBodyAndParsesAccepted() throws Exception {
        canned.set(new CannedResponse(202, """
                {"indexing_task_id": "abc-123", "entity_type": "courseware-page", "entity_id": "p1"}
                """));

        RagIndexAcceptedResponse resp = client("dev").indexNow(
                RagEntityType.COURSEWARE_PAGE, "p1", "hello", Map.of("k", "v")
        );

        CapturedRequest req = captured.get();
        assertThat(req.method).isEqualTo("POST");
        assertThat(req.path).isEqualTo("/v1/rag/index/courseware-page");
        Map<String, Object> body = mapper.readValue(req.body, Map.class);
        assertThat(body).containsEntry("entity_id", "p1");
        assertThat(body).containsEntry("content", "hello");
        assertThat(body).containsKey("metadata");

        assertThat(resp.indexingTaskId()).isEqualTo("abc-123");
        assertThat(resp.entityId()).isEqualTo("p1");
    }

    @Test
    void indexNowFiveHundredMapsToRagServiceException() {
        canned.set(new CannedResponse(500, "{\"detail\":\"boom\"}"));
        assertThatThrownBy(() ->
                client("dev").indexNow(RagEntityType.COURSEWARE_PAGE, "p1", "hello", Map.of()))
                .isInstanceOf(RagServiceException.class)
                .hasMessageContaining("indexNow")
                .hasMessageContaining("courseware-page:p1");
    }

    @Test
    void deleteNowSendsDeleteAnd204Returns() {
        canned.set(new CannedResponse(204, ""));
        client("dev").deleteNow(RagEntityType.MEMORY, "1:event:42");

        CapturedRequest req = captured.get();
        assertThat(req.method).isEqualTo("DELETE");
        assertThat(req.path).isEqualTo("/v1/rag/index/memory/1:event:42");
    }

    @Test
    void deleteNow404IsTreatedAsAlreadyAbsent() {
        canned.set(new CannedResponse(404, "{\"detail\":\"not found\"}"));
        client("dev").deleteNow(RagEntityType.NOTEBOOK, "abc");
    }

    @Test
    void queryFiveHundredMapsToRagServiceException() {
        canned.set(new CannedResponse(500, "{\"detail\":\"upstream embedding boom\"}"));
        assertThatThrownBy(() ->
                client("dev").queryCourseware(
                        new RagCoursewareQueryRequest(1L, "什么是变量", List.of(), 4)))
                .isInstanceOf(RagServiceException.class)
                .hasMessageContaining("query")
                .hasMessageContaining("/v1/rag/query/courseware")
                .satisfies(ex -> assertThat(((RagServiceException) ex).getStatusCode()).isEqualTo(500));
    }

    @Test
    void queryHonorsConfiguredTimeoutAndWrapsAsTransportFailure() {
        AlethicodeProperties.Rag ragProperties = new AlethicodeProperties.Rag();
        ragProperties.setBaseUrl("http://127.0.0.1:" + port);
        ragProperties.setInternalToken("dev");
        ragProperties.setQueryTimeoutSeconds(1);
        ragProperties.setConnectTimeoutSeconds(1);
        ragProperties.setIndexTimeoutSeconds(2);
        HttpRagServiceClient tight = new HttpRagServiceClient(
                ragProperties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        server.removeContext("/");
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThatThrownBy(() ->
                tight.queryCourseware(
                        new RagCoursewareQueryRequest(1L, "slow", List.of(), 4)))
                .isInstanceOf(RagServiceException.class)
                .hasMessageContaining("query transport failure for /v1/rag/query/courseware");
    }

    private record CapturedRequest(String method, String path, String token, String body) {
    }

    private static final class CannedResponse {
        final int status;
        final String body;

        CannedResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}

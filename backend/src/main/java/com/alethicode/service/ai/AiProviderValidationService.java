package com.alethicode.service.ai;

import com.alethicode.dto.request.AiProviderValidationRunRequest;
import com.alethicode.dto.response.AiProviderValidationCaseResult;
import com.alethicode.dto.response.AiProviderValidationRunResponse;
import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 面向管理员的 Spring AI Provider 合约验证服务。
 *
 * 服务运行固定样例，并只返回脱敏摘要，便于确认 provider 可用且不暴露 prompt 或 API key。
 */
@Service
public class AiProviderValidationService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderValidationService.class);

    private static final String JSON_SYSTEM_PROMPT =
            "Return exactly this JSON object: {\"status\":\"ok\",\"steps\":[\"read\",\"solve\"],\"score\":1}";
    private static final String JSON_USER_PROMPT = "Return the JSON as instructed.";
    private static final Set<String> JSON_REQUIRED_KEYS = Set.of("status", "steps", "score");

    private static final String CONTENT_PROMPT = "用20字以内的中文总结：人工智能正在改变教育";

    private final SpringAiModelGateway springGateway;
    private final SpringAiToolLoopService toolLoopService;
    private final AiModelProfileResolver profileResolver;

    public AiProviderValidationService(SpringAiModelGateway springGateway,
                                       SpringAiToolLoopService toolLoopService,
                                       AiModelProfileResolver profileResolver) {
        this.springGateway = springGateway;
        this.toolLoopService = toolLoopService;
        this.profileResolver = profileResolver;
    }

    public AiProviderValidationRunResponse createValidationRun(AiProviderValidationRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Validation request is required");
        }
        // includeEmbedding 仅为兼容历史调用方保留；embedding 链路已迁到 alethicode-rag。
        if (!request.includeJson() && !request.includeContent() && !request.includeToolLoop()) {
            throw new IllegalArgumentException("At least one validation case must be included");
        }

        String runId = UUID.randomUUID().toString();
        List<AiProviderValidationCaseResult> cases = new ArrayList<>();

        if (request.includeJson()) cases.add(runJsonCase(request.profilePrefix()));
        if (request.includeContent()) cases.add(runContentCase());
        if (request.includeToolLoop()) cases.add(runToolLoopCase(request.profilePrefix()));

        boolean allPassed = cases.stream().allMatch(AiProviderValidationCaseResult::shapeMatched);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCases", cases.size());
        summary.put("passedCases", cases.stream().filter(AiProviderValidationCaseResult::shapeMatched).count());

        return new AiProviderValidationRunResponse(runId, request.profilePrefix(), allPassed, cases, summary);
    }

    private AiProviderValidationCaseResult runJsonCase(String profilePrefix) {
        try {
            Map<String, Object> result = springGateway.callForJson(JSON_SYSTEM_PROMPT, JSON_USER_PROMPT, profilePrefix);
            List<String> missing = new ArrayList<>();
            for (String key : JSON_REQUIRED_KEYS) {
                if (!result.containsKey(key)) missing.add(key);
            }
            boolean shape = missing.isEmpty();
            String failure = shape ? null : "missing_keys=" + missing;
            Map<String, Object> summary = Map.of("keyCount", result.size(), "keys", result.keySet());
            return new AiProviderValidationCaseResult("json", true, shape, failure, summary);
        } catch (Exception e) {
            log.warn("Spring AI JSON validation failed: {}", e.getMessage());
            return new AiProviderValidationCaseResult("json", false, false, safeMessage(e), Map.of());
        }
    }

    private AiProviderValidationCaseResult runContentCase() {
        try {
            String result = springGateway.callForContent(CONTENT_PROMPT);
            boolean ok = result != null && !result.isBlank();
            Map<String, Object> summary = Map.of("length", result != null ? result.length() : 0);
            return new AiProviderValidationCaseResult(
                    "content", ok, ok, ok ? null : "blank response", summary);
        } catch (Exception e) {
            log.warn("Spring AI content validation failed: {}", e.getMessage());
            return new AiProviderValidationCaseResult("content", false, false, safeMessage(e), Map.of());
        }
    }

    private AiProviderValidationCaseResult runToolLoopCase(String profilePrefix) {
        List<ToolDefinition> tools = List.of(new ToolDefinition(
                "validation_echo",
                "Echo the given message back verbatim",
                Map.of(
                        "type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")
                )
        ));
        Map<String, ToolExecutor> executors = Map.of("validation_echo", args -> {
            String msg = String.valueOf(args.getOrDefault("message", ""));
            return Map.of("echo", msg);
        });
        String systemPrompt = "You have a tool named 'validation_echo'. " +
                "First call validation_echo with message='hello'. " +
                "Then respond with exactly the JSON object {\"tool_seen\":true}.";
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "Please use the validation_echo tool, then return the required JSON.")
        );
        StoppingCondition sc = new StoppingCondition(3, 3, 3, 60);

        try {
            AiModelProfile profile = profileResolver.resolveChat(profilePrefix);
            ReactResult result = toolLoopService.execute(
                    systemPrompt, messages, tools, executors, 3, null, sc, profile, "required");
            boolean hasToolSeen = result.result().containsKey("tool_seen");
            int traceCount = result.toolTraceEntries().size();
            boolean shape = hasToolSeen && traceCount > 0;
            String failure = null;
            if (!hasToolSeen) failure = "missing tool_seen key";
            else if (traceCount == 0) failure = "no trace entries";
            Map<String, Object> summary = Map.of(
                    "traceCount", traceCount,
                    "iterationsUsed", result.iterationsUsed()
            );
            return new AiProviderValidationCaseResult("toolLoop", shape, shape, failure, summary);
        } catch (Exception e) {
            log.warn("Spring AI tool loop validation failed: {}", e.getMessage());
            return new AiProviderValidationCaseResult("toolLoop", false, false, safeMessage(e), Map.of());
        }
    }

    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        // 截断 provider 错误，避免响应中暴露上游载荷。
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }
}

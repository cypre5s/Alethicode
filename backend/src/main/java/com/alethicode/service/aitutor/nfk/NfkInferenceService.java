package com.alethicode.service.aitutor.nfk;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.alethicode.config.AlethicodeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * NFK 知识追踪 ONNX 推理服务。
 *
 * <p>输入契约严格对齐 {@code research/nfk/inference/predictor.py}：
 * <ul>
 *   <li>{@code question_ids}：{@code INT64} {@code [1, T]}</li>
 *   <li>{@code skill_ids}：{@code INT64} {@code [1, T]}</li>
 *   <li>{@code responses}：{@code INT64} {@code [1, T]}（0/1）</li>
 *   <li>{@code delta_t}：{@code FLOAT} {@code [1, T, T]}，{@code delta_t[i][j] = max(0, (t_i - t_j) / 86400.0)}</li>
 *   <li>{@code pad_mask}：{@code BOOL} {@code [1, T]}，全 {@code false}（当前调用方都不补 padding）</li>
 * </ul>
 *
 * <p>输出契约：
 * <ul>
 *   <li>{@code outputs[0]}：shape {@code [1, T]} 的 raw logits，需要手动做 {@code sigmoid} 得到 mastery 概率；</li>
 *   <li>{@code outputs[1]}（可选）：attention weights，本 service 暂不用。</li>
 * </ul>
 *
 * <p>服务设计原则：
 * <ul>
 *   <li>{@link #isAvailable()} 仅在 Spring 启动阶段一次性探测 ONNX 模型是否存在且可加载；推理路径无额外检查；</li>
 *   <li>加载失败时保留 {@code available=false}，所有 {@code predict*} 方法抛出 {@link IllegalStateException}（failfast，由上层决定是否降级 BKT）；</li>
 *   <li>SessionOptions 默认 CPU；GPU 由外层通过配置决定（当前不暴露 GPU 开关）；</li>
 *   <li>{@link OrtSession} 线程安全（onnxruntime 官方声明），直接复用单例。</li>
 * </ul>
 */
@Service
public class NfkInferenceService {

    private static final Logger log = LoggerFactory.getLogger(NfkInferenceService.class);

    private final AlethicodeProperties properties;

    /**
     * NFK sparse attention 内部对 {@code TopK} 的 k 硬编码为 20，
     * 所以推理时序列长度必须 ≥ 20；不足的前置 padding + pad_mask=true。
     * 该值必须与 {@code research/nfk/} 训练侧 {@code sparse_attn.top_k} 和 ONNX 导出参数保持一致。
     */
    private static final int MIN_SEQUENCE_LENGTH = 20;

    private OrtEnvironment environment;
    private OrtSession session;
    private List<String> inputNames;
    private List<String> outputNames;
    private boolean available = false;

    public NfkInferenceService(AlethicodeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        AlethicodeProperties.Nfk cfg = properties.getNfk();
        if (!cfg.isEnabled()) {
            log.info("NFK inference disabled via alethicode.nfk.enabled=false");
            return;
        }
        String rawPath = cfg.getModelPath();
        if (rawPath == null || rawPath.isBlank()) {
            log.info("NFK inference skipped: alethicode.nfk.model-path is empty");
            return;
        }
        Path modelPath = Path.of(rawPath);
        if (!Files.isRegularFile(modelPath)) {
            log.warn("NFK inference model not found at {}; will fall back to BKT", modelPath);
            return;
        }
        try {
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = environment.createSession(modelPath.toString(), options);
            inputNames = List.copyOf(session.getInputNames());
            outputNames = List.copyOf(session.getOutputNames());
            available = true;
            log.info("NFK inference ready: model={}, inputs={}, outputs={}",
                    modelPath, inputNames, outputNames);
        } catch (OrtException e) {
            log.warn("NFK inference failed to initialize: {}", e.getMessage());
            session = null;
            environment = null;
            available = false;
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException ignored) {
        }
        session = null;
        environment = null;
        available = false;
    }

    public boolean isAvailable() {
        return available;
    }

    public List<String> inputNames() {
        return inputNames == null ? List.of() : inputNames;
    }

    public List<String> outputNames() {
        return outputNames == null ? List.of() : outputNames;
    }

    /**
     * 对单个学生的交互序列进行 NFK 推理。
     *
     * @return 每个 {@code skill_id} 最终的 mastery 概率；若某个 skill 出现多次，取最后一次。
     */
    public Map<Long, Double> predictPerSkill(List<NfkInteraction> interactions) {
        if (!available) {
            throw new IllegalStateException("NFK inference is not available");
        }
        if (interactions == null || interactions.isEmpty()) {
            return Map.of();
        }

        int realT = interactions.size();
        int padLen = Math.max(0, MIN_SEQUENCE_LENGTH - realT);
        int t = realT + padLen;

        long[] questionIds = new long[t];
        long[] skillIds = new long[t];
        long[] responses = new long[t];
        double[] timestamps = new double[t];
        boolean[] padMask = new boolean[t];

        for (int i = 0; i < padLen; i++) {
            padMask[i] = true;
        }
        for (int i = 0; i < realT; i++) {
            NfkInteraction it = interactions.get(i);
            Objects.requireNonNull(it, "interaction must not be null");
            int dst = padLen + i;
            questionIds[dst] = it.questionId();
            skillIds[dst] = it.skillId();
            responses[dst] = it.response();
            timestamps[dst] = it.timestampSeconds();
        }

        float[][][] deltaT = buildDeltaT(timestamps);

        try (OnnxTensor questionTensor = OnnxTensor.createTensor(
                environment, LongBuffer.wrap(questionIds), new long[]{1, t});
             OnnxTensor skillTensor = OnnxTensor.createTensor(
                     environment, LongBuffer.wrap(skillIds), new long[]{1, t});
             OnnxTensor responseTensor = OnnxTensor.createTensor(
                     environment, LongBuffer.wrap(responses), new long[]{1, t});
             OnnxTensor deltaTensor = OnnxTensor.createTensor(environment, deltaT);
             OnnxTensor padTensor = createBoolTensor(environment, padMask)) {

            Map<String, OnnxTensor> feeds = new HashMap<>();
            feeds.put("question_ids", questionTensor);
            feeds.put("skill_ids", skillTensor);
            feeds.put("responses", responseTensor);
            feeds.put("delta_t", deltaTensor);
            feeds.put("pad_mask", padTensor);

            try (OrtSession.Result result = session.run(feeds)) {
                float[] raw = extractFirstOutput(result, t);
                long[] realSkillIds = new long[realT];
                double[] realMastery = new double[realT];
                for (int i = 0; i < realT; i++) {
                    realSkillIds[i] = skillIds[padLen + i];
                    realMastery[i] = sigmoid(raw[padLen + i]);
                }
                return aggregateLastValuePerSkill(realSkillIds, realMastery);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("NFK inference failed: " + e.getMessage(), e);
        }
    }

    /**
     * 便捷方法：交互列表 + 一组 KC 过滤条件 → 返回仅包含指定 {@code skill_id} 的 mastery。
     * 调用方可以用此方法把 NFK 输出投影回 "当前 problem 的 KC" 子集。
     */
    public Map<Long, Double> predictForSkills(List<NfkInteraction> interactions, List<Long> wantedSkillIds) {
        if (wantedSkillIds == null || wantedSkillIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> all = predictPerSkill(interactions);
        if (all.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> filtered = new LinkedHashMap<>();
        for (Long skillId : wantedSkillIds) {
            Double value = all.get(skillId);
            if (value != null) {
                filtered.put(skillId, value);
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    private static float[][][] buildDeltaT(double[] timestamps) {
        int t = timestamps.length;
        float[][][] arr = new float[1][t][t];
        for (int i = 0; i < t; i++) {
            for (int j = 0; j < t; j++) {
                double delta = (timestamps[i] - timestamps[j]) / 86400.0;
                if (delta < 0.0) {
                    delta = 0.0;
                }
                arr[0][i][j] = (float) delta;
            }
        }
        return arr;
    }

    /**
     * onnxruntime-java 1.17 的 {@code createTensor(env, buffer, shape)} 不直接支持 bool，
     * 但可以通过 {@link ByteBuffer}（每字节一个 bool，0/1）+ {@code OnnxJavaType.BOOL} 侧写；
     * 这里改走 {@code OnnxTensor.createTensor(env, primitive array, OnnxJavaType)} 的同类重载。
     */
    private static OnnxTensor createBoolTensor(OrtEnvironment env, boolean[] padMask) throws OrtException {
        int t = padMask.length;
        boolean[][] reshaped = new boolean[1][t];
        System.arraycopy(padMask, 0, reshaped[0], 0, t);
        return OnnxTensor.createTensor(env, reshaped);
    }

    private static float[] extractFirstOutput(OrtSession.Result result, int expectedLength) throws OrtException {
        if (result == null || result.size() == 0) {
            throw new IllegalStateException("NFK onnx returned empty result");
        }
        OnnxValue firstValue = result.get(0);
        if (!(firstValue instanceof OnnxTensor tensor)) {
            throw new IllegalStateException("NFK first output is not a tensor: " + firstValue);
        }
        Object raw = tensor.getValue();
        float[] flat;
        if (raw instanceof float[][] matrix) {
            if (matrix.length != 1 || matrix[0].length != expectedLength) {
                throw new IllegalStateException(
                        "NFK output shape mismatch: expected [1," + expectedLength + "] got ["
                                + matrix.length + "," + (matrix.length == 0 ? 0 : matrix[0].length) + "]");
            }
            flat = matrix[0];
        } else if (raw instanceof float[] vec) {
            if (vec.length != expectedLength) {
                throw new IllegalStateException(
                        "NFK output length mismatch: expected " + expectedLength + " got " + vec.length);
            }
            flat = vec;
        } else {
            throw new IllegalStateException("NFK first output has unexpected type: " + raw.getClass());
        }
        return flat;
    }

    private static Map<Long, Double> aggregateLastValuePerSkill(long[] skillIds, double[] mastery) {
        Map<Long, Double> lastValue = new LinkedHashMap<>();
        for (int i = 0; i < skillIds.length; i++) {
            lastValue.put(skillIds[i], mastery[i]);
        }
        return Collections.unmodifiableMap(lastValue);
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * 一次交互的 NFK 输入记录。
     *
     * @param questionId       题目 ID（与训练集 question_ids 对齐）
     * @param skillId          主 KC ID（与训练集 skill_ids 对齐）
     * @param response         回答结果（0 或 1；通常 result=0 的 submission → response=1）
     * @param timestampSeconds UNIX 秒数，用于构造 delta_t
     */
    public record NfkInteraction(long questionId, long skillId, int response, double timestampSeconds) {
    }
}

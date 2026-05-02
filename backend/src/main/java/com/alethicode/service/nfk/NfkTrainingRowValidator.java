package com.alethicode.service.nfk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 行级校验 NFK 训练 CSV 中单条记录是否满足
 * {@code contracts/nfk/training_dataset.schema.json} 契约。
 *
 * <p>schema 文件由 Maven 在 {@code <build><resources>} 阶段从仓库根目录的
 * {@code contracts/nfk/training_dataset.schema.json} 复制到 backend 的 classpath
 * {@code /contracts/nfk/training_dataset.schema.json}，与 Python 训练侧
 * {@code research/nfk/data/contract_validator.py} 共享同一份 source of truth。
 *
 * <p>校验失败时抛 {@link NfkTrainingRowValidationException}，调用方负责把异常
 * 翻译成对外契约违反消息（如 HTTP 500 与日志诊断），不在此处自行降级。
 */
@Component
public class NfkTrainingRowValidator {

    public static final String SCHEMA_CLASSPATH = "/contracts/nfk/training_dataset.schema.json";

    private final ObjectMapper objectMapper;
    private JsonSchema schema;

    public NfkTrainingRowValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @PostConstruct
    void initSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream stream = getClass().getResourceAsStream(SCHEMA_CLASSPATH)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "NFK training row schema not found on classpath: " + SCHEMA_CLASSPATH
                                + ". Maven build is expected to copy contracts/nfk/training_dataset.schema.json "
                                + "into target/classes/contracts/nfk/ via the pom.xml <resources> block.");
            }
            this.schema = factory.getSchema(stream);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load NFK training row schema", e);
        }
    }

    /**
     * 校验单行训练样本。
     *
     * @param rowNumber 1-based 行号（不含 header），用于错误消息定位
     * @param row 单行字段映射，键名必须与 schema 字段一致
     * @throws NfkTrainingRowValidationException schema 不通过时抛出
     */
    public void validateRow(long rowNumber, Map<String, Object> row) {
        if (row == null) {
            throw new NfkTrainingRowValidationException(rowNumber, "row payload is null");
        }
        JsonNode node = objectMapper.valueToTree(row);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            ValidationMessage first = errors.iterator().next();
            throw new NfkTrainingRowValidationException(rowNumber, first.getMessage());
        }
    }
}

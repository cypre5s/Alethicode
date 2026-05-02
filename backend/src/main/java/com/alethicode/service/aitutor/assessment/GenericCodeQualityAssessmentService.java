package com.alethicode.service.aitutor.assessment;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.language.TutorLanguageSupport;

import java.util.Map;

public class GenericCodeQualityAssessmentService implements CodeQualityAssessmentService {

    private final AiModelGateway aiModelGateway;

    public GenericCodeQualityAssessmentService(AiModelGateway aiModelGateway) {
        this.aiModelGateway = aiModelGateway;
    }

    @Override
    public Map<String, Object> assess(String code, String language, String problemDescription) {
        String normalizedCode = code == null ? "" : code.trim();
        String normalizedLanguage = TutorLanguageSupport.normalizeLanguage(language);
        if (normalizedCode.isBlank()) {
            throw new IllegalStateException("code is required for code quality assessment");
        }
        if (normalizedLanguage.isBlank()) {
            throw new IllegalStateException("language is required for code quality assessment");
        }
        String codeFence = TutorLanguageSupport.codeFenceLanguage(normalizedLanguage);
        Map<String, Object> raw = aiModelGateway.callForJson(
                """
                你是 %s 代码质量评估助手。请严格评估初学者代码质量并返回结构化 JSON。
                """.formatted(TutorLanguageSupport.displayLanguage(normalizedLanguage)),
                """
                【题目描述】
                %s

                【学生代码】
                ```%s
                %s
                ```

                请输出 JSON：
                {
                  "readability": 1,
                  "readability_comment": "建议",
                  "efficiency": 1,
                  "efficiency_comment": "建议",
                  "style": 1,
                  "style_comment": "建议"
                }
                """.formatted(problemDescription == null ? "" : problemDescription.trim(), codeFence, normalizedCode)
        );
        return PythonCodeQualityAssessmentService.normalize(raw);
    }
}

package com.alethicode.service.languagepack.impl;

import com.alethicode.service.ai.AiModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class KcPrerequisiteDetectorService {

    private static final Logger log = LoggerFactory.getLogger(KcPrerequisiteDetectorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiModelGateway aiModelGateway;

    public KcPrerequisiteDetectorService(JdbcTemplate jdbcTemplate, AiModelGateway aiModelGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiModelGateway = aiModelGateway;
    }

    @Transactional
    public Map<String, Object> detectAndPersist(Long languagePackId) {
        List<Map<String, Object>> kcs = jdbcTemplate.queryForList("""
            SELECT k.id, k.name, k.description, c.title AS chapter_title, c.chapter_index
            FROM language_pack_kc k
            JOIN language_pack_chapter c ON c.id = k.chapter_id
            WHERE c.language_pack_id = ?
            ORDER BY c.chapter_index, k.id
            """, languagePackId);

        if (kcs.isEmpty()) {
            return Map.of("status", "no_kcs", "language_pack_id", languagePackId);
        }

        StringBuilder kcList = new StringBuilder();
        for (Map<String, Object> kc : kcs) {
            kcList.append("- ID=").append(kc.get("id"))
                  .append(" 名称=\"").append(kc.get("name")).append("\"")
                  .append(" 章节=\"").append(kc.get("chapter_title")).append("\"");
            if (kc.get("description") != null && !kc.get("description").toString().isBlank()) {
                kcList.append(" 描述=\"").append(kc.get("description")).append("\"");
            }
            kcList.append("\n");
        }

        String systemPrompt = """
                你是编程教育课程设计专家。
                分析以下知识点列表，判定它们之间的前置依赖关系（A 是 B 的前提条件）。
                只输出确定的、教学上必要的直接依赖关系，不要传递性依赖。
                
                输出 JSON：
                {
                  "edges": [
                    {"from": <前置KC的ID>, "to": <依赖KC的ID>}
                  ]
                }
                
                规则：
                - from 是前置条件，to 是依赖该前置的知识点
                - 只包含直接依赖，不包含间接传递
                - 不能产生循环依赖
                - 没有前置关系的知识点不需要出现
                """;

        String userPrompt = "以下是语言包（ID=%d）的所有知识点：\n%s".formatted(languagePackId, kcList);

        Map<String, Object> result = aiModelGateway.callForJson(systemPrompt, userPrompt);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");
        if (edges == null || edges.isEmpty()) {
            return Map.of("status", "no_edges_detected", "language_pack_id", languagePackId);
        }

        jdbcTemplate.update(
                "DELETE FROM language_pack_kc_prerequisite WHERE language_pack_id = ?",
                languagePackId);

        int inserted = 0;
        for (Map<String, Object> edge : edges) {
            Number fromId = (Number) edge.get("from");
            Number toId = (Number) edge.get("to");
            if (fromId == null || toId == null) continue;

            try {
                jdbcTemplate.update("""
                    INSERT INTO language_pack_kc_prerequisite (kc_id, prerequisite_kc_id, language_pack_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT (kc_id, prerequisite_kc_id) DO NOTHING
                    """, toId.longValue(), fromId.longValue(), languagePackId);
                inserted++;
            } catch (Exception e) {
                log.warn("Failed to insert prerequisite edge: from={} to={}: {}",
                        fromId, toId, e.getMessage());
            }
        }

        log.info("KC prerequisite detection completed: languagePackId={}, kcCount={}, edgesDetected={}, edgesInserted={}",
                languagePackId, kcs.size(), edges.size(), inserted);

        return Map.of(
                "status", "completed",
                "language_pack_id", languagePackId,
                "kc_count", kcs.size(),
                "edges_detected", edges.size(),
                "edges_inserted", inserted
        );
    }
}

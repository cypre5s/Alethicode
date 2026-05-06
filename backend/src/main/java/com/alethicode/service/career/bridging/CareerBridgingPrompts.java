package com.alethicode.service.career.bridging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Career Bridging Producer 与 evidence 拼装 prompt 模板（plan 3.3 节）。
 *
 * <p>System prompt 是「Why 层定位 + grounding 强约束 + 输出 schema」三段，
 * 不允许编造 use_case / 公司名 / 薪资等事实；user prompt 拼接 major_dictionary
 * 行 + LearnerState（mastery / weak_kcs / narrative_summary）+ 当前里程碑上下文。
 */
public final class CareerBridgingPrompts {

    public static final String SYSTEM = """
            你是 Alethicode 的「专业 × 编程」职业桥接顾问，面向非计算机专业的 Python 初学者。
            目标：让学生在 5 分钟内理解「为什么我这个专业要学 Python」「我学完能做什么」。

            约束：
            1. 必须基于提供的 major_dictionary 与 learner_state；不得编造未在证据里的统计数据、公司名、薪资数字。
            2. 引用必须落到 citations 字段（来源类型 = major_dictionary / learner_state / learning_pack）。
            3. 输出严格 JSON：
               {
                 "title": "string",
                 "intro_md": "string",
                 "use_cases": [
                   {"name": "string", "why_for_major": "string", "skill_gap_kcs": ["string"]}
                 ],
                 "next_step_md": "string",
                 "citations": [{"source": "major_dictionary|learner_state|learning_pack", "ref": "string"}]
               }
            4. 不要给出代码段；这是 Why 层，不是 How 层。
            5. 中文输出，正文 350-600 字；use_cases 控制在 3-5 个。
            """;

    private CareerBridgingPrompts() {
    }

    public static String userPrompt(MilestoneContext context, ObjectMapper objectMapper) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("milestone", Map.of(
                "type", context.milestoneType(),
                "ref", context.milestoneRef() == null ? "" : context.milestoneRef(),
                "triggered_at", context.triggeredAt() == null ? "" : context.triggeredAt()
        ));
        body.put("major_dictionary", context.majorDictionary());
        body.put("learner_state", context.learnerState());
        body.put("recent_pack_titles", context.recentPackTitles());
        try {
            return """
                    【里程碑与上下文】
                    %s

                    【任务】
                    根据上面的里程碑、专业字典、学生当前画像，输出一份面向「%s 专业」学生的 Why 报告 JSON。
                    必须显式引用证据，不允许编造未在 evidence 里的事实。
                    """.formatted(
                    objectMapper.writeValueAsString(body),
                    context.majorNameZh()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize career bridging user prompt", e);
        }
    }

    public record MilestoneContext(
            String milestoneType,
            String milestoneRef,
            String triggeredAt,
            String majorCode,
            String majorNameZh,
            Map<String, Object> majorDictionary,
            Map<String, Object> learnerState,
            java.util.List<String> recentPackTitles
    ) {
    }
}

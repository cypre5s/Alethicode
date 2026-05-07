package com.alethicode.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
abstract class AITutorWorkflowIntegrationTestSupport extends AbstractJdbcIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean protected JudgeServerService judgeServerService;
    @MockBean protected SystemAdminService systemAdminService;
    @MockBean protected ReleaseNotesService releaseNotesService;
    @MockBean protected PlatformConfigService platformConfigService;
    @MockBean protected SystemOptionService systemOptionService;
    @MockBean protected AdminUploadService adminUploadService;
    @MockBean protected AiModelGateway aiModelGateway;

    @BeforeEach
    void seedAiWorkflowFixture() {
        when(aiModelGateway.callForJson(anyString(), anyString())).thenAnswer(invocation -> {
            String systemPrompt = invocation.getArgument(0, String.class);
            String prompt = invocation.getArgument(1, String.class);
            if (systemPrompt.contains("OJ 完整示例生成助手")) {
                return Map.of(
                        "analogy_problem", Map.of(
                                "title", "统计正数个数",
                                "description", "给定一个整数列表，统计其中正数的个数"
                        ),
                        "steps", List.of(
                                Map.of("subgoal", "初始化计数器", "code", "count = 0", "explanation", "先准备一个计数变量"),
                                Map.of("subgoal", "遍历列表元素", "code", "for num in numbers:", "explanation", "逐个查看列表中的元素"),
                                Map.of("subgoal", "判断是否满足条件", "code", "    if num > 0:", "explanation", "只统计正数"),
                                Map.of("subgoal", "更新计数器", "code", "        count += 1", "explanation", "满足条件时让计数加一")
                        ),
                        "bridge_to_current", "现在把同样的思路迁移到你的题目上，先想清楚条件判断写什么。"
                );
            }
            if (systemPrompt.contains("OJ 渐退示例生成助手")) {
                return Map.of(
                        "steps", List.of(
                                Map.of("step_id", "step_0", "subgoal", "初始化计数器", "code", "count = 0", "explanation", "先准备计数变量", "hint", "先准备计数器"),
                                Map.of("step_id", "step_1", "subgoal", "遍历列表元素", "code", "for num in numbers:", "explanation", "逐个读取元素", "hint", "需要遍历列表"),
                                Map.of("step_id", "step_2", "subgoal", "判断是否满足条件", "code", "    if num % 2 == 0:", "explanation", "这里只统计偶数", "hint", "补上条件判断"),
                                Map.of("step_id", "step_3", "subgoal", "更新计数器", "code", "        count += 1", "explanation", "满足条件时更新答案", "hint", "满足条件时该做什么")
                        )
                );
            }
            if (systemPrompt.contains("OJ 渐退示例校验助手")) {
                return Map.of(
                        "validation_status", "completed",
                        "step_feedback", List.of(
                                Map.of("step_id", "step_0", "status", "correct", "feedback", "初始化写对了"),
                                Map.of("step_id", "step_1", "status", "correct", "feedback", "遍历步骤也写对了"),
                                Map.of("step_id", "step_3", "status", "correct", "feedback", "更新计数器也写对了")
                        )
                );
            }
            if (systemPrompt.contains("OJ 最小提示生成助手")) {
                return Map.of(
                        "hint", "这道题的核心还是遍历和条件计数，先把主循环写出来。",
                        "relevant_kcs", List.of("循环", "条件判断"),
                        "nudge", "你已经掌握这类思路了，直接开始写代码吧。"
                );
            }
            if (prompt.contains("\"plain_task\"")
                    || prompt.contains("\"problem_explanation\"")
                    || prompt.contains("\"warmup_question\"")
                    || prompt.contains("\"courseware_refs\"")) {
                return Map.of(
                        "plain_task", "这道题让你先读入两个数字，再输出它们的和。",
                        "problem_explanation", "核心是把输入解析正确，再完成一次简单计算。",
                        "input_translation", "输入一行，包含两个整数。",
                        "output_translation", "输出一个整数，表示两个数的和。",
                        "approach_direction", "先确认输入格式，再写求和语句。",
                        "warmup_question", "如果输入是 1 2，程序应该输出什么？",
                        "courseware_refs", List.of()
                );
            }
            if (prompt.contains("\"blocks\"")) {
                return Map.of(
                        "title", "加法题代码拼图",
                        "mode", "pure_parsons",
                        "blocks", List.of(
                                Map.of("id", "b2", "code", "a, b = map(int, input().split())"),
                                Map.of("id", "b1", "code", "total = a + b"),
                                Map.of("id", "b3", "code", "print(total)")
                        ),
                        "answer", List.of("b2", "b1", "b3"),
                        "blanks", Map.of()
                );
            }
            if (prompt.contains("\"celebration\"")) {
                return Map.of(
                        "celebration", "这次通过很扎实。",
                        "what_you_learned", List.of("输入解析", "基础加法"),
                        "key_success_point", "你正确完成了主流程。",
                        "transfer_tip", "下次可以先写样例。",
                        "one_improvement", "变量命名还能更清晰。",
                        "recommended_review", "输入输出",
                        "next_practice_direction", "继续练习同类基础题",
                        "peer_comparison", Map.of(
                                "algorithm_diff", "优秀解法会先抽出输入处理。",
                                "structure_diff", "结构更紧凑。",
                                "organization_diff", "输出逻辑更集中。"
                        ),
                        "progressive_hints", List.of(
                                Map.of("title", "先抽输入", "question", "你能把输入处理单独想清楚吗？", "code_snippet", "a, b = map(int, input().split())"),
                                Map.of("title", "再做输出", "question", "结果变量该怎么命名？", "code_snippet", "print(a + b)")
                        )
                );
            }
            if (systemPrompt.contains("错误诊断助手")
                    || prompt.contains("\"root_cause\"")
                    || prompt.contains("【失败样例证据（来自判题返回）】")) {
                return Map.of(
                        "root_cause", "根据题干、错误代码和错误样例，你在边界处理上出现了偏差。",
                        "what_program_is_doing", "程序执行到了判题阶段，但在失败样例上输出与要求不一致。",
                        "expected_behavior", "程序应严格按题目输入输出规则处理，并在失败样例上得到期望结果。",
                        "fix_direction", "先用失败样例手推关键变量，再对照题干修正状态更新逻辑。",
                        "related_kcs", List.of("边界条件", "状态更新"),
                        "encouragement", "你已经定位到关键范围了，下一步只改这一处就好。"
                );
            }
            if (prompt.contains("\"reference_solution_code\"")
                    || prompt.contains("\"target_kcs\"")
                    || prompt.contains("\"test_cases\"")) {
                return Map.of(
                        "title", "迁移练习：数组求和升级版",
                        "description", "给定一组整数，统计满足条件的子段数量。",
                        "input_description", "输入包含 n 和数组 a。",
                        "output_description", "输出满足条件的子段个数。",
                        "hint", "先想清楚状态定义，再考虑边界。",
                        "reference_solution_code", "print(4)",
                        "test_cases", List.of(Map.of("input", "5\n1 2 3 4 5", "output", "4")),
                        "samples", List.of(Map.of("input", "5\n1 2 3 4 5", "output", "4")),
                        "target_kcs", List.of("循环", "边界条件")
                );
            }
            if (prompt.contains("\"reply\"")
                    && prompt.contains("\"focus_point\"")
                    && prompt.contains("\"next_question\"")) {
                return Map.of(
                        "reply", "先别急着写完整代码，先把输入输出链路确认清楚。",
                        "focus_point", "输入输出链路",
                        "next_question", "如果输入是 1 2，你期望输出什么？"
                );
            }
            if (prompt.contains("\"step_explanations\"")) {
                return Map.of(
                        "step_explanations", List.of(
                                Map.of("step_index", 0, "explanation", "先读取输入，确认程序从哪一步开始处理数据。"),
                                Map.of("step_index", 1, "explanation", "这里更新了关键变量，后面的输出会依赖它。"),
                                Map.of("step_index", 2, "explanation", "最后一步把当前变量结果输出出来。")
                        ),
                        "divergence_step", 1
                );
            }
            return Map.of(
                    "title", "迁移练习：数组求和升级版",
                    "description", "给定一组整数，统计满足条件的子段数量。",
                    "input_description", "输入包含 n 和数组 a。",
                    "output_description", "输出满足条件的子段个数。",
                    "hint", "先想清楚状态定义，再考虑边界。",
                    "reference_solution_code", "print(4)",
                    "test_cases", List.of(Map.of("input", "5\n1 2 3 4 5", "output", "4")),
                    "samples", List.of(Map.of("input", "5\n1 2 3 4 5", "output", "4")),
                    "target_kcs", List.of("循环", "边界条件")
            );
        });

        long rootId = insertUser("root", "Admin");
        long studentId = insertUser("student", "Regular User");

        insertProblem(1001L, "2.1.005", "A+B", true, false, null, rootId);
        insertProblem(2001L, "P2001", "Source", true, false, null, rootId);
        insertProblem(2002L, "P2002", "AI Pending", false, true, 2001L, rootId);
        insertProblem(2003L, "P2003", "AI Pending 2", false, true, 2001L, rootId);
        jdbcTemplate.update(
                """
                update problem
                set description = '给定一组数字，求和输出。',
                    input_description = '输入一行数字。',
                    output_description = '输出求和结果。',
                    samples = cast(? as jsonb),
                    reference_solution_language = 'Python3',
                    reference_solution_code = 'a, b = map(int, input().split())\ntotal = a + b\nprint(total)',
                    template = cast(? as jsonb)
                where id = 1001
                """,
                """
                [
                  {"case_type":"normal","input":"1 2","output":"3"},
                  {"case_type":"invalid","input":"a b","output":"非法输入"}
                ]
                """,
                "{\"Python3\":\"a, b = map(int, input().split())\\n# TODO: 输出结果\\n\"}"
        );

        jdbcTemplate.update(
                "insert into ai_knowledge_component(id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess) values (1, '循环', 'Loop', '1', 'loop', 0.3, 0.2, 0.1, 0.2)"
        );
        jdbcTemplate.update(
                "insert into ai_problem_kc_mapping(problem_id, kc_id, weight) values (1001, 1, 0.8)"
        );

        jdbcTemplate.update(
                "insert into ai_misconception(id, kc_id, source, status, name, description, correction_hint, evidence_count) values ('mc-p1', 1, 'mcmining', 'pending', 'OffByOne', 'desc', 'hint', 3)"
        );
        jdbcTemplate.update(
                "insert into ai_misconception(id, kc_id, source, status, name, description, correction_hint, evidence_count) values ('mc-p2', 1, 'mcmining', 'pending', 'Boundary', 'desc', 'hint', 2)"
        );
        jdbcTemplate.update(
                "insert into ai_misconception(id, kc_id, source, status, name, description, correction_hint, evidence_count) values ('mc-target', 1, 'manual', 'approved', 'Target', 'desc', 'hint', 1)"
        );

        jdbcTemplate.update(
                "insert into ai_learning_event(user_id, problem_id, event_type, extra_data) values (?, 1001, 'misconception_detected_ast', cast('{\"detector_name\":\"loop_detector\"}' as jsonb))",
                studentId
        );
        jdbcTemplate.update(
                "insert into ai_learning_event(user_id, problem_id, event_type, extra_data) values (?, 1001, 'preflight_go_edit', cast('{\"detector_name\":\"loop_detector\",\"question\":\"请检查循环边界\",\"hint\":\"手动模拟 i=0 和 i=n-1\"}' as jsonb))",
                studentId
        );
        jdbcTemplate.update(
                "insert into ai_learning_event(user_id, problem_id, event_type, extra_data) values (?, 1001, 'preflight_force_submit', cast('{\"detector_name\":\"loop_detector\"}' as jsonb))",
                studentId
        );
    }

    protected String createWorkflowSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/workflow/session")
                        .with(SecurityMockMvcRequestPostProcessors.user("student").roles("USER"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"problem_id\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.session_id").isNotEmpty())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("session_id").asText();
    }

    protected long findUserId(String username) {
        Long userId = jdbcTemplate.queryForObject(
                "select id from \"user\" where username = ?",
                Long.class,
                username
        );
        assertThat(userId).isNotNull();
        return userId;
    }

    protected long insertUser(String username, String adminType) {
        Long id = jdbcTemplate.queryForObject(
                """
                insert into "user"(username, admin_type, create_time)
                values (?, ?, now())
                returning id
                """,
                Long.class,
                username,
                adminType
        );
        assertThat(id).isNotNull();
        return id;
    }

    protected void insertProblem(Long id, String displayId, String title, boolean visible, boolean aiGenerated, Long sourceProblemId, Long creatorId) {
        jdbcTemplate.update(
                """
                insert into problem(id, _id, title, visible, is_public, difficulty, submission_number, accepted_number,
                                    is_ai_generated, ai_source_problem_id, created_by_id, test_case_score, create_time)
                values (?, ?, ?, ?, true, 'Low', 0, 0, ?, ?, ?, cast('[]' as jsonb), now())
                """,
                id,
                displayId,
                title,
                visible,
                aiGenerated,
                sourceProblemId,
                creatorId
        );
    }
}

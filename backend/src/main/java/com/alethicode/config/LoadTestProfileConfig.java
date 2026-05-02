package com.alethicode.config;

import com.alethicode.service.ai.AiModelGateway;
import com.alethicode.service.aitutor.contract.StoppingCondition;
import com.alethicode.service.aitutor.react.ReactResult;
import com.alethicode.service.aitutor.react.ToolContext;
import com.alethicode.service.aitutor.react.ToolDefinition;
import com.alethicode.service.aitutor.react.ToolExecutor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

@Configuration
@Profile("loadtest")
public class LoadTestProfileConfig {

    private static final long LOADTEST_PROBLEM_ID = 910001L;
    private static final long LOADTEST_KC_ID = 910001L;

    @Bean
    @Primary
    public AiModelGateway loadTestAiModelGateway() {
        return new AiModelGateway() {
            @Override
            public Map<String, Object> callForJson(String systemPrompt, String userPrompt) {
                if (userPrompt.contains("\"plain_task\"")) {
                    return Map.of(
                            "plain_task", "读入两个整数并输出它们的和。",
                            "problem_explanation", "先把输入拆成两个整数，再做一次基础加法。",
                            "input_translation", "输入一行，包含两个整数。",
                            "output_translation", "输出一个整数，表示两个数的和。",
                            "approach_direction", "先解析输入，再计算结果，最后打印。",
                            "warmup_question", "如果输入是 2 3，输出应该是多少？",
                            "courseware_refs", List.of()
                    );
                }
                if (userPrompt.contains("\"blocks\"")) {
                    return Map.of(
                            "title", "加法代码拼图",
                            "mode", "pure_parsons",
                            "blocks", List.of(
                                    Map.of("id", "b1", "code", "a, b = map(int, input().split())"),
                                    Map.of("id", "b2", "code", "total = a + b"),
                                    Map.of("id", "b3", "code", "print(total)")
                            ),
                            "answer", List.of("b1", "b2", "b3"),
                            "blanks", Map.of()
                    );
                }
                if (userPrompt.contains("\"celebration\"")) {
                    return Map.of(
                            "celebration", "做得很好，这次主链完整跑通了。",
                            "what_you_learned", List.of("输入解析", "基础加法"),
                            "key_success_point", "你把输入、计算和输出串起来了。",
                            "transfer_tip", "下一次先写样例再开始编码。",
                            "one_improvement", "变量命名还可以再清晰一点。",
                            "recommended_review", "输入输出基础",
                            "next_practice_direction", "继续练习基础模拟题",
                            "peer_comparison", Map.of(
                                    "algorithm_diff", "优秀解法会先稳定输入处理。",
                                    "structure_diff", "结构更紧凑。",
                                    "organization_diff", "计算和输出分层更清楚。"
                            ),
                            "progressive_hints", List.of(
                                    Map.of("title", "先处理输入", "question", "你能先把输入拆出来吗？", "code_snippet", "a, b = map(int, input().split())")
                            )
                    );
                }
                return Map.of(
                        "title", "迁移练习：三数求和",
                        "description", "读入三个整数，输出它们的和。",
                        "input_description", "输入一行，包含三个整数。",
                        "output_description", "输出一个整数。",
                        "hint", "先确认输入个数，再做加法。",
                        "reference_solution_code", "a, b, c = map(int, input().split())\nprint(a + b + c)",
                        "test_cases", List.of(Map.of("input", "1 2 3", "output", "6")),
                        "samples", List.of(Map.of("input", "1 2 3", "output", "6")),
                        "target_kcs", List.of("输入输出", "基础运算")
                );
            }

            @Override
            public Map<String, Object> callForJson(String systemPrompt, String userPrompt, String profilePrefix) {
                return callForJson(systemPrompt, userPrompt);
            }

            @Override
            public Map<String, Object> callForJsonCached(String cacheKey, String systemPrompt, String userPrompt, String profilePrefix) {
                return callForJson(systemPrompt, userPrompt);
            }

            @Override
            public String callForContent(String userPrompt) {
                return "load test content";
            }

            @Override
            public ReactResult callWithTools(String systemPrompt, List<Map<String, Object>> messages,
                                             List<ToolDefinition> tools, Map<String, ToolExecutor> executors,
                                             int maxIterations, ToolContext toolContext,
                                             StoppingCondition stoppingCondition, String profilePrefix) {
                return new ReactResult(Map.of("loadtest", true), 1, List.of());
            }

            @Override
            public String readRequiredConfig(String key) {
                return "loadtest-value";
            }

            @Override
            public String readConfigOrDefault(String key, String defaultValue) {
                return defaultValue;
            }
        };
    }

    @Bean
    public ApplicationRunner loadTestBootstrapRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            Integer userCount = jdbcTemplate.queryForObject(
                    "select count(*) from \"user\" where username = ?",
                    Integer.class,
                    "loadtest"
            );
            if (userCount == null || userCount == 0) {
                Long userId = jdbcTemplate.queryForObject(
                        """
                        insert into "user"(username, email, password_hash, admin_type, problem_permission, is_disabled, create_time)
                        values (?, ?, ?, 'Regular User', 'None', false, now())
                        returning id
                        """,
                        Long.class,
                        "loadtest",
                        "loadtest@example.com",
                        encoder.encode("LoadTestPass123!")
                );
                jdbcTemplate.update(
                        """
                        insert into user_profile(user_id, acm_problems_status, oi_problems_status, role, real_name)
                        values (?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                        on conflict (user_id) do nothing
                        """,
                        userId,
                        "{}",
                        "{}",
                        "Student",
                        "Load Test User"
                );
            }

            Integer problemCount = jdbcTemplate.queryForObject(
                    "select count(*) from problem where id = ?",
                    Integer.class,
                    LOADTEST_PROBLEM_ID
            );
            if (problemCount == null || problemCount == 0) {
                Long creatorId = jdbcTemplate.queryForObject(
                        "select id from \"user\" where username = ?",
                        Long.class,
                        "loadtest"
                );
                jdbcTemplate.update(
                        """
                        insert into problem(
                            id, _id, title, description, input_description, output_description,
                            samples, test_case_id, test_case_score, hint,
                            languages, template, created_by_id, time_limit, memory_limit,
                            visible, difficulty, source, submission_number, accepted_number,
                            statistic_info, is_ai_generated, visibility_status, reference_solution_language, reference_solution_code, create_time
                        ) values (
                            ?, ?, ?, ?, ?, ?,
                            cast(? as jsonb), ?, cast(? as jsonb), ?,
                            cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                            ?, ?, ?, ?, ?,
                            cast(? as jsonb), ?, ?, ?, ?, now()
                        )
                        """,
                        LOADTEST_PROBLEM_ID,
                        "LT-AI-001",
                        "Load Test A+B",
                        "给定两个整数，输出它们的和。",
                        "输入一行，包含两个整数。",
                        "输出一个整数。",
                        "[{\"input\":\"1 2\",\"output\":\"3\"}]",
                        "loadtest-tc",
                        "[]",
                        "注意基础输入输出。",
                        "[\"Python3\"]",
                        "{\"Python3\":\"a, b = map(int, input().split())\\nprint(a + b)\\n\"}",
                        creatorId,
                        1000,
                        256,
                        true,
                        "Low",
                        "loadtest",
                        0,
                        0,
                        "{}",
                        false,
                        "public",
                        "Python3",
                        "a, b = map(int, input().split())\nprint(a + b)"
                );
            }

            Integer kcCount = jdbcTemplate.queryForObject(
                    "select count(*) from ai_knowledge_component where id = ?",
                    Integer.class,
                    LOADTEST_KC_ID
            );
            if (kcCount == null || kcCount == 0) {
                jdbcTemplate.update(
                        """
                        insert into ai_knowledge_component(id, name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess)
                        values (?, '输入输出', 'InputOutput', 'loadtest', 'loadtest kc', 0.3, 0.2, 0.1, 0.2)
                        """,
                        LOADTEST_KC_ID
                );
            }

            Integer mappingCount = jdbcTemplate.queryForObject(
                    "select count(*) from ai_problem_kc_mapping where problem_id = ? and kc_id = ?",
                    Integer.class,
                    LOADTEST_PROBLEM_ID,
                    LOADTEST_KC_ID
            );
            if (mappingCount == null || mappingCount == 0) {
                jdbcTemplate.update(
                        "insert into ai_problem_kc_mapping(problem_id, kc_id, weight) values (?, ?, ?)",
                        LOADTEST_PROBLEM_ID,
                        LOADTEST_KC_ID,
                        1.0
                );
            }

            Integer coursewareCount = jdbcTemplate.queryForObject(
                    "select count(*) from ai_courseware_chunk where problem_id = ?",
                    Integer.class,
                    LOADTEST_PROBLEM_ID
            );
            if (coursewareCount == null || coursewareCount == 0) {
                jdbcTemplate.update(
                        """
                        insert into ai_courseware_chunk(classroom_id, lesson_id, chapter, kc_id, problem_id, title, content, metadata)
                        values (0, 0, 'loadtest', ?, ?, 'Load Test Courseware', '先确认输入，再做基础加法。', cast(? as jsonb))
                        """,
                        LOADTEST_KC_ID,
                        LOADTEST_PROBLEM_ID,
                        "{\"source\":\"loadtest\"}"
                );
            }
        };
    }
}

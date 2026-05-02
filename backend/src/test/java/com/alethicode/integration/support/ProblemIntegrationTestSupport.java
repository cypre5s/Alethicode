package com.alethicode.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "alethicode.system.test-case-dir=/tmp/alethicode-java-problem-module-it-testcases"
})
abstract class ProblemIntegrationTestSupport extends AbstractJdbcIntegrationTest {

    protected static final String TEST_CASE_DIR = "/tmp/alethicode-java-problem-module-it-testcases";

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

    protected long studentId;

    @BeforeEach
    void seedProblemFixture() {
        cleanTestCaseDir();

        jdbcTemplate.update(
                "insert into \"user\"(username, create_time, admin_type, problem_permission, is_disabled) values (?, now(), ?, ?, ?)",
                "student",
                "Regular User",
                "None",
                false
        );
        jdbcTemplate.update(
                "insert into \"user\"(username, create_time, admin_type, problem_permission, is_disabled) values (?, now(), ?, ?, ?)",
                "root",
                "Admin",
                "All",
                false
        );
        studentId = jdbcTemplate.queryForObject(
                "select id from \"user\" where username = ?",
                Long.class,
                "student"
        );

        jdbcTemplate.update(
                "insert into user_profile(user_id, acm_problems_status, real_name) values (?, cast(? as jsonb), ?)",
                studentId,
                "{}",
                "Student A"
        );

        jdbcTemplate.update(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    visible, difficulty, source, submission_number, accepted_number,
                    statistic_info, is_ai_generated, visibility_status
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, ?
                )
                """,
                "PPT2-001",
                "Two Sum",
                "<p>desc</p>",
                "<p>input</p>",
                "<p>output</p>",
                "[{\"input\":\"1 2\",\"output\":\"3\"}]",
                "tc-1",
                "[]",
                "<p>hint</p>",
                "[\"Python3\"]",
                "{\"Python3\":\"//PREPEND BEGIN\\n\\n//PREPEND END\\n\\n//TEMPLATE BEGIN\\nprint(1)\\n//TEMPLATE END\\n\\n//APPEND BEGIN\\n\\n//APPEND END\"}",
                studentId,
                1000,
                256,
                true,
                "Low",
                "book",
                10,
                5,
                "{}",
                false,
                "class_private"
        );
        seedProblemTestCase("tc-1");

        jdbcTemplate.update("insert into problem_tag(name) values (?)", "dp");
        jdbcTemplate.update("insert into problem_tag(name) values (?)", "type:coding");

        jdbcTemplate.update(
                """
                insert into problem_problem_tags(problem_id, problemtag_id)
                select p.id, t.id
                from problem p, problem_tag t
                where p._id = ? and t.name in (?, ?)
                """,
                "PPT2-001",
                "dp",
                "type:coding"
        );

        Long problemPk = jdbcTemplate.queryForObject(
                "select id from problem where _id = ? order by id desc limit 1",
                Long.class,
                "PPT2-001"
        );
        String acmStatusJson = "{\"problems\":{\"" + problemPk + "\":{\"status\":0,\"_id\":\"PPT2-001\"}}}";
        jdbcTemplate.update(
                "update user_profile set acm_problems_status = cast(? as jsonb) where user_id = ?",
                acmStatusJson,
                studentId
        );
    }

    @AfterEach
    void cleanupProblemFixture() {
        cleanTestCaseDir();
    }

    protected void cleanTestCaseDir() {
        Path base = Path.of(TEST_CASE_DIR);
        try {
            if (Files.exists(base)) {
                try (var paths = Files.walk(base)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException exception) {
                                    throw new IllegalStateException("failed to clean testcase dir: " + path, exception);
                                }
                            });
                }
            }
            Files.createDirectories(base);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to setup testcase dir", exception);
        }
    }

    protected byte[] buildTestCaseZip() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutput = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zipOutput.putNextEntry(new ZipEntry("1.in"));
                zipOutput.write("1 2\r\n".getBytes(StandardCharsets.UTF_8));
                zipOutput.closeEntry();
                zipOutput.putNextEntry(new ZipEntry("1.out"));
                zipOutput.write("3\r\n".getBytes(StandardCharsets.UTF_8));
                zipOutput.closeEntry();
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to build test case zip fixture", exception);
        }
    }

    protected byte[] buildFpsXml(String memoryUnit) {
        String xml = """
                <fps version="1.2">
                  <item>
                    <title>FPS Imported</title>
                    <description><![CDATA[<p>desc</p>]]></description>
                    <input><![CDATA[<p>in</p>]]></input>
                    <output><![CDATA[<p>out</p>]]></output>
                    <hint><![CDATA[hint]]></hint>
                    <source><![CDATA[fps-source]]></source>
                    <time_limit unit="s">2</time_limit>
                    <memory_limit unit="%s">256</memory_limit>
                    <template language="Python">print(input())</template>
                    <prepend language="Python"># prep</prepend>
                    <append language="Python"># app</append>
                    <sample_input>1</sample_input>
                    <sample_output>2</sample_output>
                    <test_input>1</test_input>
                    <test_output>2</test_output>
                  </item>
                </fps>
                """.formatted(memoryUnit);
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    protected Set<String> readZipEntryNames(byte[] zipBytes) {
        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>();
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to parse zip payload", exception);
        }
    }

    protected void seedProblemTestCase(String testCaseId) {
        try {
            Path dir = Path.of(TEST_CASE_DIR, testCaseId);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("1.in"), "1 2\n", StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("1.out"), "3\n", StandardCharsets.UTF_8);
            Map<String, Object> info = Map.of(
                    "spj", false,
                    "test_cases", Map.of(
                            "1", Map.of(
                                    "input_name", "1.in",
                                    "output_name", "1.out",
                                    "input_size", 4,
                                    "output_size", 2,
                                    "stripped_output_md5", "eccbc87e4b5ce2fe28308fd9f2a7baf3"
                            )
                    )
            );
            Files.writeString(
                    dir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("failed to seed testcase fixture", exception);
        }
    }

    protected MockMultipartFile buildTestCaseMultipart(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/zip", bytes);
    }

    protected MockMultipartFile buildXmlMultipart(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/xml", bytes);
    }
}

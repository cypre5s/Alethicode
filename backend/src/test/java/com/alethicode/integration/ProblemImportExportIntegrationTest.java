package com.alethicode.integration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemImportExportIntegrationTest extends ProblemIntegrationTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    void adminTestCasesUploadAndDownloadShouldWorkOnDatabaseBackedFlow() throws Exception {
        MockMultipartFile invalidFile = buildTestCaseMultipart("tc.zip", buildTestCaseZip());
        mockMvc.perform(multipart("/api/admin/test-cases")
                        .file(invalidFile)
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data").value("Upload failed"));

        MockMultipartFile file = buildTestCaseMultipart("tc.zip", buildTestCaseZip());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/admin/test-cases")
                        .file(file)
                        .param("spj", "false")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        Map<String, Object> wrapped = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) wrapped.get("data");
        String testCaseId = String.valueOf(data.get("id"));

        Path storedDir = Path.of(TEST_CASE_DIR, testCaseId);
        assertThat(Files.exists(storedDir.resolve("1.in"))).isTrue();
        assertThat(Files.exists(storedDir.resolve("1.out"))).isTrue();
        assertThat(Files.exists(storedDir.resolve("info"))).isTrue();

        Long problemId = jdbcTemplate.queryForObject(
                "select id from problem where _id = ?",
                Long.class,
                "PPT2-001"
        );
        jdbcTemplate.update("update problem set test_case_id = ? where id = ?", testCaseId, problemId);

        MvcResult downloadResult = mockMvc.perform(get("/api/admin/test-cases")
                        .param("problem_id", String.valueOf(problemId))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=problem_" + problemId + "_test_cases.zip"))
                .andReturn();

        assertThat(readZipEntryNames(downloadResult.getResponse().getContentAsByteArray())).contains("1.in", "1.out", "info");
    }

    @SuppressWarnings("unchecked")
    @Test
    void exportAndImportProblemsShouldRoundTripOnDatabaseBackedFlow() throws Exception {
        Long problemId = jdbcTemplate.queryForObject(
                "select id from problem where _id = ?",
                Long.class,
                "PPT2-001"
        );

        MvcResult exportResult = mockMvc.perform(get("/api/admin/export-problems")
                        .param("problem_id", String.valueOf(problemId))
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment;filename=problem-export.zip"))
                .andReturn();

        byte[] exportZip = exportResult.getResponse().getContentAsByteArray();
        assertThat(readZipEntryNames(exportZip)).contains("1/problem.json", "1/testcase/1.in", "1/testcase/1.out");

        jdbcTemplate.execute("delete from problem_problem_tags");
        jdbcTemplate.execute("delete from problem");

        MockMultipartFile importFile = buildTestCaseMultipart("problem-export.zip", exportZip);
        MvcResult importResult = mockMvc.perform(multipart("/api/admin/import-problems")
                        .file(importFile)
                        .param("auto_kc", "true")
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.import_count").value(1))
                .andReturn();

        Map<String, Object> wrapped = objectMapper.readValue(importResult.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) wrapped.get("data");
        assertThat(data.get("kc_bindcount")).isEqualTo(0);
        assertThat(data.get("kc_auto_bindcount")).isEqualTo(0);

        Integer count = jdbcTemplate.queryForObject("select count(*) from problem", Integer.class);
        assertThat(count).isEqualTo(1);
        String difficulty = jdbcTemplate.queryForObject(
                "select difficulty from problem where _id = ?",
                String.class,
                "PPT2-001"
        );
        assertThat(difficulty).isEqualTo("Low");
    }

    @Test
    void importFpsShouldCreateProblemAndTestCases() throws Exception {
        MockMultipartFile fpsFile = buildXmlMultipart("fps.xml", buildFpsXml("MB"));

        mockMvc.perform(multipart("/api/admin/import-fps")
                        .file(fpsFile)
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.import_count").value(1));

        Map<String, Object> imported = jdbcTemplate.queryForMap(
                "select _id, test_case_id, time_limit, memory_limit from problem where _id like 'fps-%' order by id desc limit 1"
        );
        String displayId = String.valueOf(imported.get("_id"));
        assertThat(displayId).startsWith("fps-");
        assertThat(((Number) imported.get("time_limit")).intValue()).isEqualTo(2000);
        assertThat(((Number) imported.get("memory_limit")).intValue()).isEqualTo(256);

        String testCaseId = String.valueOf(imported.get("test_case_id"));
        Path testCaseDir = Path.of(TEST_CASE_DIR, testCaseId);
        assertThat(Files.exists(testCaseDir.resolve("1.in"))).isTrue();
        assertThat(Files.exists(testCaseDir.resolve("1.out"))).isTrue();
        assertThat(Files.exists(testCaseDir.resolve("info"))).isTrue();

        String pythonTemplate = jdbcTemplate.queryForObject(
                "select template->>'Python3' from problem where _id = ?",
                String.class,
                displayId
        );
        assertThat(pythonTemplate).contains("//PREPEND BEGIN");
        assertThat(pythonTemplate).contains("//TEMPLATE BEGIN");
        assertThat(pythonTemplate).contains("//APPEND BEGIN");
    }

    @Test
    void importFpsShouldRejectInvalidMemoryUnit() throws Exception {
        MockMultipartFile fpsFile = buildXmlMultipart("fps.xml", buildFpsXml("KB"));

        mockMvc.perform(multipart("/api/admin/import-fps")
                        .file(fpsFile)
                        .with(user("root").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("error"))
                .andExpect(jsonPath("$.data", containsString("Parse FPS file error")));
    }
}

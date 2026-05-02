package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.LegacyBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdminTestCaseServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldRequireAuthentication() {
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                Mockito.mock(JdbcTemplate.class),
                new ObjectMapper(),
                propertiesWithTempDir()
        );

        ApiResponse<Object> response = service.uploadTestCases("false", mockZipFile(), null);

        assertThat(response.error()).isEqualTo("permission-denied");
        assertThat(response.data()).isEqualTo("请先登录");
    }

    @Test
    void uploadShouldRejectWhenSpjParameterMissing() {
        JdbcTemplate jdbcTemplate = mockAdminPermissionJdbc("Admin", "All", false);
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                propertiesWithTempDir()
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken("root", "N/A", List.of());
        assertThatThrownBy(() -> service.uploadTestCases(null, mockZipFile(), authentication))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("error");
                    assertThat(exception.getMessage()).isEqualTo("Upload failed");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadShouldStoreZipFilesAndReturnWrappedPayload() throws Exception {
        JdbcTemplate jdbcTemplate = mockAdminPermissionJdbc("Admin", "All", false);
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                propertiesWithTempDir()
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken("root", "N/A", List.of());
        ApiResponse<Object> response = service.uploadTestCases("false", mockZipFile(), authentication);

        assertThat(response.error()).isNull();
        Map<String, Object> payload = (Map<String, Object>) response.data();
        assertThat(payload.get("spj")).isEqualTo(false);
        assertThat(String.valueOf(payload.get("id"))).hasSize(32);
        assertThat((List<Map<String, Object>>) payload.get("info")).hasSize(1);

        String testCaseId = String.valueOf(payload.get("id"));
        Path dir = tempDir.resolve(testCaseId);
        assertThat(Files.exists(dir.resolve("1.in"))).isTrue();
        assertThat(Files.readString(dir.resolve("1.in"), StandardCharsets.UTF_8)).isEqualTo("1 2\n");
        assertThat(Files.exists(dir.resolve("info"))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadShouldValidateProblemIdParameter() throws Exception {
        JdbcTemplate jdbcTemplate = mockAdminPermissionJdbc("Admin", "All", false);
        ObjectMapper objectMapper = new ObjectMapper();
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                jdbcTemplate,
                objectMapper,
                propertiesWithTempDir()
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken("root", "N/A", List.of());
        ResponseEntity<Resource> response = service.downloadTestCases("", authentication);
        String json = new String(response.getBody().getContentAsByteArray(), StandardCharsets.UTF_8);
        Map<String, Object> payload = objectMapper.readValue(json, Map.class);

        assertThat(payload.get("error")).isEqualTo("error");
        assertThat(payload.get("data")).isEqualTo("Parameter error, problem_id is required");
    }

    @Test
    void getInlineShouldRequireProblemIdParameter() {
        JdbcTemplate jdbcTemplate = mockAdminPermissionJdbc("Admin", "All", false);
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                propertiesWithTempDir()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("root", "N/A", List.of());

        assertThatThrownBy(() -> service.getInlineTestCases("", authentication))
                .isInstanceOfSatisfying(LegacyBusinessException.class, exception -> {
                    assertThat(exception.legacyCode()).isEqualTo("error");
                    assertThat(exception.getMessage()).isEqualTo("题目 ID 不能为空");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadInlineShouldStoreCaseFilesAndReturnWrappedPayload() throws Exception {
        JdbcTemplate jdbcTemplate = mockAdminPermissionJdbc("Admin", "All", false);
        AdminTestCaseServiceImpl service = new AdminTestCaseServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                propertiesWithTempDir()
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken("root", "N/A", List.of());

        ApiResponse<Object> response = service.uploadInlineTestCases(
                Map.of("cases", List.of(
                        Map.of("input", "1 2", "output", "3"),
                        Map.of("input", "3 4", "output", "7")
                )),
                authentication
        );

        assertThat(response.error()).isNull();
        Map<String, Object> payload = (Map<String, Object>) response.data();
        String testCaseId = String.valueOf(payload.get("id"));
        assertThat(testCaseId).hasSize(32);
        assertThat((List<Map<String, Object>>) payload.get("info")).hasSize(2);
        assertThat(Files.readString(tempDir.resolve(testCaseId).resolve("1.in"), StandardCharsets.UTF_8)).isEqualTo("1 2\n");
        assertThat(Files.readString(tempDir.resolve(testCaseId).resolve("2.out"), StandardCharsets.UTF_8)).isEqualTo("7\n");
        assertThat(Files.exists(tempDir.resolve(testCaseId).resolve("info"))).isTrue();
    }

    private AlethicodeProperties propertiesWithTempDir() {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setTestCaseDir(tempDir.toString());
        return properties;
    }

    private MockMultipartFile mockZipFile() {
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
            return new MockMultipartFile("file", "tc.zip", "application/zip", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create zip test fixture", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate mockAdminPermissionJdbc(String adminType, String permission, boolean disabled) {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq("root")
        )).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(1);
            ResultSet resultSet = Mockito.mock(ResultSet.class);
            when(resultSet.getString("admin_type")).thenReturn(adminType);
            when(resultSet.getString("problem_permission")).thenReturn(permission);
            when(resultSet.getBoolean("is_disabled")).thenReturn(disabled);
            return mapper.mapRow(resultSet, 0);
        });
        return jdbcTemplate;
    }
}

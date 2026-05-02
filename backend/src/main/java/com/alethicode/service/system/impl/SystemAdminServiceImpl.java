package com.alethicode.service.system.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.OrphanTestCaseResponse;
import com.alethicode.service.system.SystemAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SystemAdminServiceImpl implements SystemAdminService {

    private static final Pattern TEST_CASE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{32}$");

    private final JdbcTemplate jdbcTemplate;
    private final AlethicodeProperties properties;

    @Autowired
    public SystemAdminServiceImpl(
            JdbcTemplate jdbcTemplate,
            AlethicodeProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public List<OrphanTestCaseResponse> getOrphanTestCases() {
        return getOrphanTestCasePaths().stream()
                .map(path -> new OrphanTestCaseResponse(
                        path.getFileName().toString(),
                        lastModifiedMillis(path)
                ))
                .sorted(Comparator.comparingLong(OrphanTestCaseResponse::createTime).reversed())
                .toList();
    }

    @Override
    public void deleteOrphanTestCase(String testCaseId) {
        Path path = resolveTestCasePath(testCaseId);
        deleteDirectoryIfExists(path);
    }

    @Override
    public void deleteAllOrphanTestCases() {
        getOrphanTestCasePaths().forEach(this::deleteDirectoryIfExists);
    }

    private List<Path> getOrphanTestCasePaths() {
        String testCaseDir = properties.getSystem().getTestCaseDir();
        if (testCaseDir == null || testCaseDir.isBlank()) {
            return List.of();
        }

        Set<String> dbIds = jdbcTemplate.queryForList(
                "select test_case_id from problem where test_case_id is not null",
                String.class
        ).stream().collect(Collectors.toSet());

        Path root = Path.of(testCaseDir);
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> TEST_CASE_ID_PATTERN.matcher(path.getFileName().toString()).matches())
                    .filter(path -> !dbIds.contains(path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan test case directory", exception);
        }
    }

    private Path resolveTestCasePath(String testCaseId) {
        if (testCaseId == null || !TEST_CASE_ID_PATTERN.matcher(testCaseId).matches()) {
            throw new IllegalArgumentException("Invalid test case id");
        }
        String testCaseDir = properties.getSystem().getTestCaseDir();
        if (testCaseDir == null || testCaseDir.isBlank()) {
            throw new IllegalArgumentException("Test case directory is not configured");
        }
        return Path.of(testCaseDir, testCaseId);
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read test case timestamp", exception);
        }
    }

    private void deleteDirectoryIfExists(Path path) {
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to delete test case directory", exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to traverse test case directory", exception);
        }
    }
}

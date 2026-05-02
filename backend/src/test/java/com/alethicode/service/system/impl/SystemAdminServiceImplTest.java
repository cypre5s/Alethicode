package com.alethicode.service.system.impl;

import com.alethicode.config.AlethicodeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAdminServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void orphanTestCasesShouldBeListedAndDeleted() throws Exception {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getSystem().setTestCaseDir(tempDir.toString());
        SystemAdminServiceImpl service = new SystemAdminServiceImpl(
                jdbcTemplate,
                properties
        );

        Path orphan = Files.createDirectory(tempDir.resolve("0123456789abcdef0123456789abcdef"));
        Files.createDirectory(tempDir.resolve("fedcba9876543210fedcba9876543210"));
        Files.createDirectory(tempDir.resolve("ignore-me"));

        when(jdbcTemplate.queryForList("select test_case_id from problem where test_case_id is not null", String.class))
                .thenReturn(List.of("fedcba9876543210fedcba9876543210"));

        var orphans = service.getOrphanTestCases();

        assertThat(orphans).hasSize(1);
        assertThat(orphans.getFirst().id()).isEqualTo("0123456789abcdef0123456789abcdef");

        service.deleteOrphanTestCase("0123456789abcdef0123456789abcdef");
        assertThat(Files.exists(orphan)).isFalse();
    }
}

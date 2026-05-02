package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemQueryService;
import com.alethicode.service.languagepack.ProblemPackageWriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AdminProblemCommandServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AdminProblemQueryService adminProblemQueryService;

    @Mock
    private ProblemPackageWriteService problemPackageWriteService;

    @Test
    void createShouldRejectWhenNoAuthentication() {
        AdminProblemCommandServiceImpl service = new AdminProblemCommandServiceImpl(
                jdbcTemplate,
                new ObjectMapper(),
                new AlethicodeProperties(),
                adminProblemQueryService,
                problemPackageWriteService
        );

        ApiResponse<Object> response = service.createProblem(minimalRequest(null), null);

        assertThat(response.error()).isEqualTo("permission-denied");
        assertThat(response.data()).isEqualTo("请先登录");
    }

    private AdminProblemUpsertRequest minimalRequest(Long id) {
        return new AdminProblemUpsertRequest(
                id,
                "PPT2-1",
                1L,
                "title",
                "desc",
                "in",
                "out",
                List.of(new com.alethicode.dto.request.ProblemSampleRequest("1", "1")),
                "tc",
                List.of(new com.alethicode.dto.request.ProblemTestCaseScoreRequest("1.in", "1.out", 100)),
                1000,
                256,
                List.of("Python3"),
                Map.of("Python3", "print(1)"),
                null,
                null,
                true,
                "Low",
                List.of("dp"),
                "",
                "",
                Map.of()
        );
    }
}

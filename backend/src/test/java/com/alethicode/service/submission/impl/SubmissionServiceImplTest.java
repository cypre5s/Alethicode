package com.alethicode.service.submission.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.DebugSubmissionRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.exception.BusinessException;
import com.alethicode.service.submission.SubmissionDataCollector;
import com.alethicode.service.submission.SubmissionThrottleService;
import com.alethicode.service.submission.impl.SubmissionQueryDomainServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private SubmissionDataCollector submissionDataCollector;

    @Mock
    private com.alethicode.service.aitutor.profile.LearnerMasteryServiceUnified masteryService;

    @Mock
    private com.alethicode.service.submission.JudgeCompletedEventPublisher judgeCompletedEventPublisher;

    @Test
    void getSubmissionShouldRequireLogin() {
        SubmissionQueryDomainServiceImpl queryService = new SubmissionQueryDomainServiceImpl(
                jdbcTemplate, new ObjectMapper(), new AlethicodeProperties());

        assertThatThrownBy(() -> queryService.getSubmission("abc", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    @Test
    void submissionExistsShouldValidateProblemId() {
        SubmissionQueryDomainServiceImpl queryService = new SubmissionQueryDomainServiceImpl(
                jdbcTemplate, new ObjectMapper(), new AlethicodeProperties());

        assertThatThrownBy(() -> queryService.submissionExists(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Parameter error, problem_id is required");
    }

    @Test
    void submissionExistsShouldReturnFalseForAnonymousWhenProblemIdProvided() {
        SubmissionQueryDomainServiceImpl queryService = new SubmissionQueryDomainServiceImpl(
                jdbcTemplate, new ObjectMapper(), new AlethicodeProperties());

        ApiResponse<Object> result = queryService.submissionExists("1", null);
        assertThat(result.data()).isEqualTo(false);
    }

    @Test
    void rejudgeShouldRequireAdmin() {
        SubmissionServiceImpl service = new SubmissionServiceImpl(
                jdbcTemplate, new ObjectMapper(), new AlethicodeProperties(),
                new SubmissionThrottleService(), transactionManager,
                submissionDataCollector, masteryService, judgeCompletedEventPublisher);

        assertThatThrownBy(() -> service.rejudgeSubmission("sub-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    @Test
    void debugShouldRequireLogin() {
        SubmissionServiceImpl service = new SubmissionServiceImpl(
                jdbcTemplate, new ObjectMapper(), new AlethicodeProperties(),
                new SubmissionThrottleService(), transactionManager,
                submissionDataCollector, masteryService, judgeCompletedEventPublisher);

        assertThatThrownBy(() -> service.debugSubmission(
                new DebugSubmissionRequest(null, "Python3", "print(1)", ""),
                null, "127.0.0.1", false
        )).isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }
}

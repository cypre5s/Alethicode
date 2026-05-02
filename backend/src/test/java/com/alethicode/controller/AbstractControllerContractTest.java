package com.alethicode.controller;

import com.alethicode.service.account.AccountService;
import com.alethicode.service.betafeedback.admin.AdminBetaFeedbackService;
import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemQueryService;
import com.alethicode.service.adminproblemcommand.AdminTestCaseService;
import com.alethicode.service.admin.AdminUploadService;
import com.alethicode.service.announcement.AnnouncementService;
import com.alethicode.service.betafeedback.BetaFeedbackService;
import com.alethicode.service.submission.JudgeServerService;
import com.alethicode.service.system.PlatformConfigService;
import com.alethicode.service.problem.ProblemQueryService;
import com.alethicode.service.announcement.ReleaseNotesService;
import com.alethicode.service.system.SystemAdminService;
import com.alethicode.service.system.SystemOptionService;
import com.alethicode.service.aitutor.impl.AITutorServiceImpl;
import com.alethicode.service.aitutor.impl.AITutorWorkflowAdminServiceImpl;
import com.alethicode.service.classroom.ClassroomLessonService;
import com.alethicode.service.submission.impl.SubmissionServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "spring.flyway.enabled=false",
        "alethicode.judge-server.token=test-token",
        "alethicode.system.test-case-dir=/tmp/test-cases",
        "alethicode.system.upload-dir=/tmp/uploads",
        "alethicode.language-pack.storage-root=/tmp/lp-storage",
        "alethicode.language-pack.preview-dir=/tmp/lp-preview",
        "alethicode.system.classroom-lesson-dir=/tmp/lessons",
        "alethicode.stream.judge-dispatch.enabled=false",
        "spring.security.csrf.enabled=false",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.openai.chat.api-key=test-openai-key",
        "spring.ai.openai.embedding.api-key=test-openai-key"
})
abstract class AbstractControllerContractTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockBean protected org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @MockBean protected JdbcTemplate jdbcTemplate;
    @MockBean protected org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @MockBean protected JudgeServerService judgeServerService;
    @MockBean protected SystemAdminService systemAdminService;
    @MockBean protected ReleaseNotesService releaseNotesService;
    @MockBean protected PlatformConfigService platformConfigService;
    @MockBean protected SystemOptionService systemOptionService;
    @MockBean protected ProblemQueryService problemQueryService;
    @MockBean protected AdminProblemQueryService adminProblemQueryService;
    @MockBean protected AdminProblemCommandService adminProblemCommandService;
    @MockBean protected AdminTestCaseService adminTestCaseService;
    @MockBean protected SubmissionServiceImpl submissionService;
    @MockBean protected AdminUploadService adminUploadService;
    @MockBean protected AccountService accountService;
    @MockBean protected com.alethicode.service.account.AccountAuthDomainService accountAuthDomainService;
    @MockBean protected com.alethicode.service.account.AccountProfileDomainService accountProfileDomainService;
    @MockBean protected AnnouncementService announcementService;
    @MockBean protected AITutorServiceImpl aiTutorService;
    @MockBean protected AITutorWorkflowAdminServiceImpl aiTutorWorkflowAdminService;
    @MockBean protected ClassroomLessonService classroomLessonService;
    @MockBean protected com.alethicode.service.languagepack.VideoJobService videoJobService;

    @MockBean protected com.alethicode.service.aitutor.AITutorSessionDomainService aiTutorSessionDomainService;
    @MockBean protected com.alethicode.service.aitutor.AITutorAnalyticsDomainService aiTutorAnalyticsDomainService;
    @MockBean protected com.alethicode.service.aitutor.AITutorKnowledgeDomainService aiTutorKnowledgeDomainService;
    @MockBean protected com.alethicode.service.aitutor.AITutorWorkflowDomainService aiTutorWorkflowDomainService;
    @MockBean protected com.alethicode.service.aitutor.AITutorAdminReviewDomainService aiTutorAdminReviewDomainService;
    @MockBean protected com.alethicode.service.submission.SubmissionCommandDomainService submissionCommandDomainService;
    @MockBean protected com.alethicode.service.submission.SubmissionQueryDomainService submissionQueryDomainService;
    @MockBean protected com.alethicode.service.submission.SubmissionJudgeDispatchDomainService submissionJudgeDispatchDomainService;
    @MockBean protected com.alethicode.service.account.AccountAdminDomainService accountAdminDomainService;
    @MockBean protected com.alethicode.service.aitutor.profile.AITutorWelcomeService aiTutorWelcomeService;
    @MockBean protected com.alethicode.service.aitutor.profile.LearningTwinService learningTwinService;
    @MockBean protected com.alethicode.service.aitutor.profile.StrategyFeedbackService strategyFeedbackService;
    @MockBean protected com.alethicode.service.classroom.ClassroomCoreDomainService classroomCoreDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomSessionDomainService classroomSessionDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomMemberDomainService classroomMemberDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomAssignmentDomainService classroomAssignmentDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomMonitorDomainService classroomMonitorDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomLessonDomainService classroomLessonDomainService;
    @MockBean protected com.alethicode.service.classroom.ClassroomAiProblemDomainService classroomAiProblemDomainService;
    @MockBean protected com.alethicode.service.adminproblemcommand.AdminProblemMutationDomainService adminProblemMutationDomainService;
    @MockBean protected com.alethicode.service.adminproblemcommand.AdminProblemImportDomainService adminProblemImportDomainService;
    @MockBean protected com.alethicode.service.adminproblemcommand.AdminProblemExportDomainService adminProblemExportDomainService;
    @MockBean protected com.alethicode.service.adminproblemcommand.AdminProblemFpsDomainService adminProblemFpsDomainService;
    @MockBean protected BetaFeedbackService betaFeedbackService;
    @MockBean protected AdminBetaFeedbackService adminBetaFeedbackService;
}

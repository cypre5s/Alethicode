package com.alethicode.controller;

import com.alethicode.dto.request.CreateLanguagePackInitTaskRequest;
import com.alethicode.dto.response.LanguagePackInitTaskResponse.LanguagePackSummary;
import com.alethicode.dto.response.LanguagePackInitTaskResponse;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.DocumentNormalizationService;
import com.alethicode.service.languagepack.DocumentParsingService;
import com.alethicode.service.languagepack.ExampleExtractionService;
import com.alethicode.service.languagepack.KcExtractionService;
import com.alethicode.service.languagepack.LanguagePackDocumentQueryService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.LanguagePackPublishService;
import com.alethicode.service.languagepack.ProblemGenerationService;
import com.alethicode.service.languagepack.ProblemValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLanguagePackControllerContractTest extends AbstractControllerContractTest {

    @Test
    void languagePackSynchronousStepEndpointsShouldBeClosed() throws Exception {
        List<String> retiredEndpoints = List.of(
                "/api/admin/language-packs/init-tasks/1/parse",
                "/api/admin/language-packs/init-tasks/1/extract-kcs",
                "/api/admin/language-packs/init-tasks/1/extract-examples",
                "/api/admin/language-packs/init-tasks/1/generate-problems",
                "/api/admin/language-packs/init-tasks/1/validate-problems",
                "/api/admin/language-packs/init-tasks/1/publish"
        );

        for (String endpoint : retiredEndpoints) {
            mockMvc.perform(post(endpoint)
                            .with(user("admin").roles("ADMIN"))
                            .with(csrf()))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}

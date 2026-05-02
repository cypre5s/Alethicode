package com.alethicode.service.languagepack.temporal;

import com.alethicode.dto.response.LanguagePackPipelineJobResponse;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackPipelineJobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "alethicode.temporal.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledLanguagePackPipelineJobService implements LanguagePackPipelineJobService {

    @Override
    public LanguagePackPipelineJobResponse startJob(Long taskId) {
        throw disabled();
    }

    @Override
    public LanguagePackPipelineJobResponse getJob(Long taskId, String jobId) {
        throw disabled();
    }

    @Override
    public LanguagePackPipelineJobResponse cancelJob(Long taskId, String jobId) {
        throw disabled();
    }

    @Override
    public LanguagePackPipelineJobResponse retryJob(Long taskId, String jobId) {
        throw disabled();
    }

    private BusinessException disabled() {
        return new BusinessException(ErrorCode.CONFLICT, "Temporal language pack pipeline is disabled");
    }
}

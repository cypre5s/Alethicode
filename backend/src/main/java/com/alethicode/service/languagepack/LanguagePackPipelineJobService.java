package com.alethicode.service.languagepack;

import com.alethicode.dto.response.LanguagePackPipelineJobResponse;

public interface LanguagePackPipelineJobService {

    LanguagePackPipelineJobResponse startJob(Long taskId);

    LanguagePackPipelineJobResponse getJob(Long taskId, String jobId);

    LanguagePackPipelineJobResponse cancelJob(Long taskId, String jobId);

    LanguagePackPipelineJobResponse retryJob(Long taskId, String jobId);
}

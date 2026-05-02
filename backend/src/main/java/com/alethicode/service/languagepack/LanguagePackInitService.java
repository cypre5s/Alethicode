package com.alethicode.service.languagepack;

import com.alethicode.dto.request.CreateLanguagePackInitTaskRequest;
import com.alethicode.dto.response.LanguagePackInitTaskResponse;

import java.util.List;

public interface LanguagePackInitService {

    LanguagePackInitTaskResponse createTask(CreateLanguagePackInitTaskRequest request, Long creatorId);

    LanguagePackInitTaskResponse getTask(Long taskId);

    List<LanguagePackInitTaskResponse> listTasks();

    void advanceStage(Long taskId, String targetStage);

    void failTask(Long taskId, String reason);

    void restoreStage(Long taskId, String targetStage, String message);
}

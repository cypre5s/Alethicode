package com.alethicode.service.submission;

import com.alethicode.dto.request.JudgeServerHeartbeatRequest;
import com.alethicode.dto.response.JudgeServerListResponse;

public interface JudgeServerService {

    void handleHeartbeat(JudgeServerHeartbeatRequest request, String ipAddress);

    JudgeServerListResponse getActiveJudgeServers();

    void deleteJudgeServer(String hostname);

    void updateJudgeServer(Long id, boolean isDisabled);
}

package com.alethicode.service.submission;

public record JudgeCompletedEvent(
        String submissionId,
        Long userId,
        Long problemId,
        String problemDisplayId,
        int finalResult,
        String errInfo,
        String code,
        String language,
        String problemTitle,
        String problemDescription,
        String inputDescription,
        String outputDescription,
        java.util.Map<String, Object> judgeResponse,
        java.util.Map<String, Object> statisticInfo
) {
}

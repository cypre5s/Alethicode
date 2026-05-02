package com.alethicode.service.submission;

/**
 * Abstraction for judge task execution, decoupled from the dispatch mechanism.
 * Both thread-pool dispatch and Redis Stream dispatch delegate here.
 */
public interface SubmissionJudgeExecutor {

    void executeJudge(String submissionId);
}

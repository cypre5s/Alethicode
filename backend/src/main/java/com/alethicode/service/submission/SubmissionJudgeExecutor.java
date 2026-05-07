package com.alethicode.service.submission;

/**
 * 判题任务执行抽象，使线程池与 Redis Stream 调度共享同一执行入口。
 */
public interface SubmissionJudgeExecutor {

    void executeJudge(String submissionId);
}

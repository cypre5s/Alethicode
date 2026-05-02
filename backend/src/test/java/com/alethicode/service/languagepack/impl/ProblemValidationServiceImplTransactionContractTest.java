package com.alethicode.service.languagepack.impl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemValidationServiceImplTransactionContractTest {

    @Test
    void validateCandidatesShouldNotRunInsideSingleLongTransaction() throws NoSuchMethodException {
        Method method = ProblemValidationServiceImpl.class.getDeclaredMethod("validateCandidates", Long.class);

        assertThat(ProblemValidationServiceImpl.class.isAnnotationPresent(Transactional.class))
                .as("题目验证不能再由类级 @Transactional 包成整批大事务，否则进度上报会和父任务行锁发生自锁")
                .isFalse();
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("validateCandidates 不应重新引入默认事务边界，避免第 1 题就卡在 0/N")
                .isFalse();
    }
}

package com.alethicode.service.aitutor.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AITutorServiceImplCalibrationScoringTest {

    private final AITutorServiceImpl service = new AITutorServiceImpl(mock(JdbcTemplate.class), new ObjectMapper());

    @Test
    void scoreCalibrationAnswerShouldRewardStructuredConceptCoverage() throws Exception {
        double score = invokeScore(
                "loop",
                "for 循环会先初始化变量，然后判断循环条件，满足时执行循环体，最后更新变量继续下一轮。"
        );
        assertThat(score).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void scoreCalibrationAnswerShouldLimitVeryShortAnswers() throws Exception {
        double score = invokeScore("recursion", "不知道");
        assertThat(score).isLessThanOrEqualTo(0.35);
    }

    @Test
    void scoreCalibrationAnswerShouldFallbackForUnknownKcGroup() throws Exception {
        double score = invokeScore("graph", "图的遍历要先标记访问节点，再按边扩展。");
        assertThat(score).isGreaterThan(0.1);
        assertThat(score).isLessThanOrEqualTo(1.0);
    }

    private double invokeScore(String kcGroup, String answer) throws Exception {
        Method method = AITutorServiceImpl.class.getDeclaredMethod("scoreCalibrationAnswer", String.class, String.class);
        method.setAccessible(true);
        return (double) method.invoke(service, kcGroup, answer);
    }
}

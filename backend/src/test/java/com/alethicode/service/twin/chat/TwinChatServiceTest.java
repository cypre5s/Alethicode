package com.alethicode.service.twin.chat;

import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService;
import com.alethicode.service.aitutor.profile.LearnerNarrativeSummaryService.NarrativeSummary;
import com.alethicode.service.twin.health.LearningHealthAggregator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwinChatServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LearnerNarrativeSummaryService summaryService = mock(LearnerNarrativeSummaryService.class);
    private final LearningHealthAggregator healthAggregator = mock(LearningHealthAggregator.class);
    private final TwinChatService chatService = new TwinChatService(
            jdbcTemplate, summaryService, healthAggregator);

    {
        when(summaryService.loadOrGenerate(anyLong()))
                .thenReturn(new NarrativeSummary(1L, 1, "该学生近 30 天做了 50 题，AC 率 80%",
                        Map.of(), "step_by_step", null, null, false, false,
                        Instant.now(), Instant.now()));
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("frequency", Map.of("submits_30d", 50, "active_days", 15));
        health.put("due_reviews", List.of());
        when(healthAggregator.aggregate(anyLong())).thenReturn(health);
    }

    @Test
    void askStatusQuestion() {
        Map<String, Object> result = chatService.askTwin(1L, "我最近怎么样");
        assertThat(result.get("answer")).asString().contains("50 题");
        assertThat(result.get("data_source")).asString().contains("summary");
    }

    @Test
    void askWeaknessQuestionWithWeakKcs() {
        List<Map<String, Object>> weakKcs = List.of(
                Map.of("name", "递归", "mastery", 0.2),
                Map.of("name", "链表", "mastery", 0.35)
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong()))
                .thenReturn(weakKcs);

        Map<String, Object> result = chatService.askTwin(1L, "我最薄弱的是什么");
        assertThat(result.get("answer")).asString().contains("递归");
        assertThat(result.get("answer")).asString().contains("链表");
        assertThat(result.get("weak_kcs")).isNotNull();
    }

    @Test
    void askWeaknessQuestionWithNoWeakKcs() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong()))
                .thenReturn(List.of());

        Map<String, Object> result = chatService.askTwin(1L, "我有什么不会的");
        assertThat(result.get("answer")).asString().contains("没有发现明显的薄弱");
    }

    @Test
    void askNextStepQuestion() {
        List<Map<String, Object>> nextKcs = List.of(
                Map.of("name", "字典", "mastery", 0.45)
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong()))
                .thenReturn(nextKcs);

        Map<String, Object> result = chatService.askTwin(1L, "下一步该学什么");
        assertThat(result.get("answer")).asString().contains("字典");
        assertThat(result.get("suggested_kcs")).isNotNull();
    }

    @Test
    void askReviewQuestionWithDueReviews() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("frequency", Map.of());
        health.put("due_reviews", List.of(
                Map.of("title", "循环复习", "package_id", 1)
        ));
        when(healthAggregator.aggregate(anyLong())).thenReturn(health);

        Map<String, Object> result = chatService.askTwin(1L, "我需要复习什么");
        assertThat(result.get("answer")).asString().contains("循环复习");
    }

    @Test
    void askReviewQuestionWithNoDueReviews() {
        Map<String, Object> result = chatService.askTwin(1L, "我要复习什么吗");
        assertThat(result.get("answer")).asString().contains("没有待复习");
    }

    @Test
    void unknownQuestionFallsBackToSummary() {
        Map<String, Object> result = chatService.askTwin(1L, "今天天气怎么样");
        assertThat(result.get("answer")).asString().isNotEmpty();
        assertThat(result.get("data_source")).asString().contains("summary");
    }

    @Test
    void askWithEmptySummaryShowsEmptyPrompt() {
        when(summaryService.loadOrGenerate(anyLong()))
                .thenReturn(NarrativeSummary.empty(1L));

        Map<String, Object> result = chatService.askTwin(1L, "我最近状态");
        assertThat(result.get("answer")).asString().contains("先做几道题");
    }

    @Test
    void getQuickQuestionsReturns4Items() {
        List<Map<String, Object>> qs = chatService.getQuickQuestions();
        assertThat(qs).hasSize(4);
        assertThat(qs.get(0).get("text")).asString().contains("怎么样");
    }
}

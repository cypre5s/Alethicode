package com.alethicode.service.aitutor.context.impl;

import com.alethicode.exception.LegacyBusinessException;
import com.alethicode.service.aitutor.context.CoursewareContextProvider;
import com.alethicode.service.aitutor.context.CoursewareSummary;
import com.alethicode.service.languagepack.LanguagePackQaService;
import com.alethicode.service.languagepack.PageRetrievalHit;
import com.alethicode.service.languagepack.PageRetrievalService;
import com.alethicode.service.rag.RagServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link CoursewareContextProviderImpl} 三层行为：
 * - 鉴权：LanguagePackQaService.listQaPacks 返回的列表是白名单
 * - 越权：未授权 lp_id 立即抛 403，不做 RAG 检索（侧信道也封死）
 * - RAG：每个授权 lp_id 调一次 PageRetrievalService.retrieve
 * - 容错：RAG 抛 RagServiceException 时该 lp 返回空 chunks 但 summary 仍返回，对话不阻断
 */
@ExtendWith(MockitoExtension.class)
class CoursewareContextProviderImplTest {

    @Mock
    private LanguagePackQaService languagePackQaService;

    @Mock
    private PageRetrievalService pageRetrievalService;

    private CoursewareContextProvider newProvider() {
        return new CoursewareContextProviderImpl(languagePackQaService, pageRetrievalService);
    }

    @Test
    void resolvesAuthorisedCoursewareReferenceIntoSummaryWithRagChunks() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(
                packMap(42L, "Python 入门"),
                packMap(7L, "数据结构基础")
        ));
        when(pageRetrievalService.retrieve(eq(42L), eq("递归是什么？"), any())).thenReturn(List.of(
                new PageRetrievalHit(101L, 9L, "Python 入门", 12, "递归 - 概念",
                        "递归是函数自己调用自己的过程。", "递归是函数自己调用自己的过程。完整定义：……", "/preview", 0.92),
                new PageRetrievalHit(102L, 9L, "Python 入门", 13, "递归 - 例子",
                        "n! 的递归实现。", "def fact(n): return 1 if n==0 else n*fact(n-1)", "/preview", 0.81)
        ));

        List<CoursewareSummary> result = newProvider().resolveCoursewareReferences(
                "alice",
                List.of("@courseware:42"),
                "递归是什么？",
                null
        );

        assertThat(result).hasSize(1);
        CoursewareSummary summary = result.get(0);
        assertThat(summary.languagePackId()).isEqualTo(42L);
        assertThat(summary.packName()).isEqualTo("Python 入门");
        assertThat(summary.chunks()).hasSize(2);
        assertThat(summary.chunks().get(0).text()).contains("递归是函数自己调用自己");
        assertThat(summary.chunks().get(0).pageNumber()).isEqualTo(12);
        assertThat(summary.chunks().get(0).score()).isEqualTo(0.92);
        verify(pageRetrievalService, never()).retrieve(eq(7L), any(), any());
    }

    @Test
    void failsFastWith403WhenUserReferencesUnauthorisedCoursewarePack() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(
                packMap(7L, "数据结构基础")
        ));

        CoursewareContextProvider provider = newProvider();

        assertThatThrownBy(() -> provider.resolveCoursewareReferences(
                "alice",
                List.of("@courseware:42"),
                "递归",
                null
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("not accessible: 42");
        verify(pageRetrievalService, never()).retrieve(any(), any(), any());
    }

    @Test
    void degradesGracefullyWhenRagServiceFailsForOnePackButNotOthers() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(
                packMap(42L, "Python 入门"),
                packMap(7L, "数据结构基础")
        ));
        when(pageRetrievalService.retrieve(eq(42L), any(), any()))
                .thenThrow(new RagServiceException("rag down"));
        when(pageRetrievalService.retrieve(eq(7L), any(), any())).thenReturn(List.of(
                new PageRetrievalHit(201L, 11L, "数据结构基础", 4, "栈",
                        "栈是后入先出的数据结构。", "栈完整介绍……", "/preview", 0.77)
        ));

        List<CoursewareSummary> result = newProvider().resolveCoursewareReferences(
                "alice",
                List.of("@courseware:42", "@courseware:7"),
                "栈",
                null
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).languagePackId()).isEqualTo(42L);
        assertThat(result.get(0).chunks()).isEmpty();
        assertThat(result.get(1).languagePackId()).isEqualTo(7L);
        assertThat(result.get(1).chunks()).hasSize(1);
        assertThat(result.get(1).chunks().get(0).text()).contains("栈");
    }

    @Test
    void deduplicatesRepeatedSameLpIdInOneMessage() {
        when(languagePackQaService.listQaPacks("alice")).thenReturn(List.of(
                packMap(42L, "Python 入门")
        ));
        when(pageRetrievalService.retrieve(eq(42L), any(), any())).thenReturn(List.of(
                new PageRetrievalHit(101L, 9L, "Python 入门", 12, null,
                        "递归概念", null, "/preview", 0.9)
        ));

        List<CoursewareSummary> result = newProvider().resolveCoursewareReferences(
                "alice",
                List.of("@courseware:42", "@courseware:42", "@courseware:42"),
                "递归",
                null
        );

        assertThat(result).hasSize(1);
        verify(pageRetrievalService, times(1)).retrieve(eq(42L), any(), any());
    }

    @Test
    void returnsEmptyWhenNoCoursewareTokensPresent() {
        List<CoursewareSummary> result = newProvider().resolveCoursewareReferences(
                "alice",
                List.of("@card:C-V-001", "@last_error", "raw text"),
                "随便",
                null
        );

        assertThat(result).isEmpty();
        verify(languagePackQaService, never()).listQaPacks(any());
        verify(pageRetrievalService, never()).retrieve(any(), any(), any());
    }

    @Test
    void rejectsAnonymousCallerWith403() {
        CoursewareContextProvider provider = newProvider();
        assertThatThrownBy(() -> provider.resolveCoursewareReferences(
                null,
                List.of("@courseware:42"),
                "递归",
                null
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("请先登录");

        assertThatThrownBy(() -> provider.resolveCoursewareReferences(
                "  ",
                List.of("@courseware:42"),
                "递归",
                null
        ))
                .isInstanceOf(LegacyBusinessException.class)
                .hasMessageContaining("请先登录");

        verify(languagePackQaService, never()).listQaPacks(any());
    }

    private static Map<String, Object> packMap(long id, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        return map;
    }
}

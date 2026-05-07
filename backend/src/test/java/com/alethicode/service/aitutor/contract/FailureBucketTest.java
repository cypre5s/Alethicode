package com.alethicode.service.aitutor.contract;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 {@link FailureBucket} 枚举形态。
 *
 * <p>前端 {@code FAILURE_BUCKETS} 和 WebSocket 载荷契约都依赖这些值；增删枚举属于破坏性合约变更。</p>
 */
class FailureBucketTest {

    @Test
    void enumerationCoversAllPublishedBuckets() {
        List<String> expected = List.of(
                "INSUFFICIENT_EVIDENCE",
                "CONFLICTING_EVIDENCE",
                "CITATION_MISMATCH",
                "QUERY_REWRITE_REGRESSION",
                "OUT_OF_SCOPE",
                "SCHEMA_VIOLATION",
                "TOOL_EXECUTION_FAILED",
                "APPROVAL_TIMEOUT",
                "RAG_RETRIEVAL_FAILED",
                "SYSTEM_ERROR",
                "UNKNOWN"
        );
        List<String> actual = Arrays.stream(FailureBucket.values())
                .map(Enum::name)
                .toList();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void ragRetrievalFailedIsResolvableFromString() {
        assertThat(FailureBucket.from("rag_retrieval_failed"))
                .contains(FailureBucket.RAG_RETRIEVAL_FAILED);
        assertThat(FailureBucket.from("RAG_RETRIEVAL_FAILED"))
                .contains(FailureBucket.RAG_RETRIEVAL_FAILED);
    }
}

package com.alethicode.service.aitutor.contract;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the {@link FailureBucket} enumeration shape that frontend
 * {@code FAILURE_BUCKETS} (frontend/src/utils/runtimeContract.js) and
 * the WebSocket payload contract both depend on.
 *
 * <p>Adding/removing a value here is a contract-breaking change that
 * must be mirrored in {@code frontend/src/utils/runtimeContract.js}
 * and {@code frontend/tests/unit/runtime-contract.spec.js}.
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

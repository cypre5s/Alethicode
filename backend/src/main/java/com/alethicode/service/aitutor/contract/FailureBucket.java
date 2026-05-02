package com.alethicode.service.aitutor.contract;

import java.util.Arrays;
import java.util.Optional;

public enum FailureBucket {
    INSUFFICIENT_EVIDENCE,
    CONFLICTING_EVIDENCE,
    CITATION_MISMATCH,
    QUERY_REWRITE_REGRESSION,
    OUT_OF_SCOPE,
    SCHEMA_VIOLATION,
    TOOL_EXECUTION_FAILED,
    APPROVAL_TIMEOUT,
    RAG_RETRIEVAL_FAILED,
    SYSTEM_ERROR,
    UNKNOWN;

    public static Optional<FailureBucket> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(raw.trim()))
                .findFirst();
    }
}

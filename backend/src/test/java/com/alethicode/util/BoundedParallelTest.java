package com.alethicode.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedParallelTest {

    @Test
    void mapShouldRetrySingleTransportFailureSequentially() {
        List<String> items = List.of("a", "b", "c");
        Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

        List<String> results = BoundedParallel.map(items, 4, item -> {
            int current = callCounts.computeIfAbsent(item, ignored -> new AtomicInteger()).incrementAndGet();
            if ("b".equals(item) && current == 1) {
                throw new IllegalStateException("LLM request failed: HTTP/1.1 header parser received no bytes");
            }
            return item.toUpperCase();
        });

        assertThat(results).containsExactly("A", "B", "C");
        assertThat(callCounts.get("a").get()).isEqualTo(1);
        assertThat(callCounts.get("b").get()).isEqualTo(2);
        assertThat(callCounts.get("c").get()).isEqualTo(1);
    }

    @Test
    void mapShouldFailFastOnNonRetryableError() {
        assertThatThrownBy(() -> BoundedParallel.map(List.of("a"), 2, item -> {
            throw new IllegalStateException("schema mismatch");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema mismatch");
    }
}

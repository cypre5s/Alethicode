package com.alethicode.service.rag;

import com.alethicode.service.rag.dto.RagCoursewareQueryRequest;
import com.alethicode.service.rag.dto.RagEntityType;
import com.alethicode.service.rag.dto.RagIndexAcceptedResponse;
import com.alethicode.service.rag.dto.RagMemoryQueryRequest;
import com.alethicode.service.rag.dto.RagQueryHits;
import com.alethicode.service.rag.dto.RagSimilarErrorQueryRequest;
import com.alethicode.service.rag.dto.RagTransferQueryRequest;

import java.util.Map;

/**
 * Synchronous facade over the alethicode-rag HTTP service.
 *
 * <p>This interface is the only path Java code reaches the RAG service —
 * never construct {@code WebClient} requests inline. There are exactly
 * two write paths:
 *
 * <ul>
 *   <li>{@link #indexNow(RagEntityType, String, String, Map)} — direct call
 *       used only by {@link RagIndexOutboxWorker}. Application code should
 *       NOT call this; it bypasses the outbox guarantee.</li>
 *   <li>{@link #deleteNow(RagEntityType, String)} — same, mirror for the
 *       DELETE action.</li>
 * </ul>
 *
 * <p>For application code that writes business tables, use
 * {@link RagIndexQueueService#enqueueIndex} / {@link RagIndexQueueService#enqueueDelete};
 * those write the outbox row inside the same DB transaction as the
 * business INSERT/UPDATE so the index never gets ahead of the business
 * state.
 */
public interface RagServiceClient {

    RagQueryHits queryCourseware(RagCoursewareQueryRequest request);

    RagQueryHits querySimilarError(RagSimilarErrorQueryRequest request);

    RagQueryHits queryMemory(RagMemoryQueryRequest request);

    RagQueryHits queryTransfer(RagTransferQueryRequest request);

    RagIndexAcceptedResponse indexNow(
            RagEntityType entityType,
            String entityId,
            String content,
            Map<String, Object> metadata
    );

    void deleteNow(RagEntityType entityType, String entityId);

    void wakeUpPipeline();
}

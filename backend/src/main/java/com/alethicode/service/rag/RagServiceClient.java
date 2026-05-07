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
 * alethicode-rag HTTP 服务的同步访问门面。
 *
 * <p>Java 侧访问 RAG 服务必须经过本接口，不在业务代码内直接构造 {@code WebClient} 请求。
 * 写路径只有两类：
 *
 * <ul>
 *   <li>{@link #indexNow(RagEntityType, String, String, Map)} 仅由
 *       {@link RagIndexOutboxWorker} 调用，业务代码不得绕过 outbox。</li>
 *   <li>{@link #deleteNow(RagEntityType, String)} 同理，只对应 DELETE 动作。</li>
 * </ul>
 *
 * <p>业务表写入后应使用
 * {@link RagIndexQueueService#enqueueIndex} / {@link RagIndexQueueService#enqueueDelete};
 * outbox 行与业务写入处于同一事务，避免索引状态领先于业务状态。</p>
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

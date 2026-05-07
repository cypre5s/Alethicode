package com.alethicode.service.rag.dto;

/**
 * RAG outbox 行动作。
 *
 * 该枚举与 {@link RagEntityType} 分离，并与 {@code rag_index_outbox.action} 的
 * SQL CHECK 约束保持一致，避免拼写错误进入数据库。
 */
public enum RagIndexAction {
    INDEX,
    DELETE
}

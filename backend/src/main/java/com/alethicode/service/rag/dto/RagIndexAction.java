package com.alethicode.service.rag.dto;

/**
 * Outbox row action — kept distinct from {@link RagEntityType} because the
 * SQL CHECK constraint on {@code rag_index_outbox.action} restricts values
 * to {@code INDEX} / {@code DELETE}; treating them as enum values prevents
 * silent typos from leaking into the DB.
 */
public enum RagIndexAction {
    INDEX,
    DELETE
}

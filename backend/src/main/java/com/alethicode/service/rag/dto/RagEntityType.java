package com.alethicode.service.rag.dto;

/**
 * alethicode-rag 索引管线支持的实体类型。
 *
 * <p>{@code slug} 是 Python 服务和 {@code rag_index_outbox.entity_type} 共同使用的线协议值；
 * 新增类型时三处必须同步。</p>
 */
public enum RagEntityType {
    COURSEWARE_PAGE("courseware-page"),
    NOTEBOOK("notebook"),
    MEMORY("memory");

    private final String slug;

    RagEntityType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static RagEntityType fromSlug(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("entity_type slug must not be null");
        }
        for (RagEntityType type : values()) {
            if (type.slug.equals(slug)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown rag entity type: " + slug);
    }
}

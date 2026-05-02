package com.alethicode.service.rag.dto;

/**
 * Entity types supported by the alethicode-rag indexing pipeline.
 *
 * <p>The slug values are wire-level identifiers consumed by the Python
 * service ({@code services/alethicode-rag/app/schemas.py::EntityType});
 * the SQL CHECK constraint on {@code rag_index_outbox.entity_type} pins
 * the same set, so any new value must land in all three places at once.
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

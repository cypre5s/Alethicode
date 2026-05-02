package com.alethicode.service.languagepack;

import java.util.LinkedHashMap;
import java.util.Map;

public record PageRetrievalHit(
        Long pageId,
        Long documentId,
        String documentTitle,
        Integer pageNo,
        String pageTitle,
        String excerpt,
        String pageText,
        String previewAssetPath,
        double score
) {

    public Map<String, Object> toMap() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("page_id", pageId);
        item.put("document_id", documentId);
        item.put("document_title", documentTitle);
        item.put("page_no", pageNo);
        item.put("page_title", pageTitle);
        item.put("excerpt", excerpt);
        item.put("page_text", pageText);
        item.put("score", score);
        return item;
    }
}

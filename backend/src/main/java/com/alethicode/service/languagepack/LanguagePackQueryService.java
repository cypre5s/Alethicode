package com.alethicode.service.languagepack;

import java.util.List;
import java.util.Map;

public interface LanguagePackQueryService {

    List<Map<String, Object>> listPublishedPacks();

    List<Map<String, Object>> listVisiblePacks(String username);

    Map<String, Object> getPackDetail(Long languagePackId);

    List<Map<String, Object>> listPackDocuments(Long languagePackId);

    List<Map<String, Object>> listPackChapters(Long languagePackId);

    Map<String, Object> getPagePreview(Long languagePackId, Long documentId, Integer pageNo);
}

package com.alethicode.service.languagepack;

import java.util.List;
import java.util.Map;

public interface LanguagePackDocumentQueryService {

    List<Map<String, Object>> listDocuments(Long taskId);

    List<Map<String, Object>> listPages(Long documentId);

    Map<String, Object> getPage(Long languagePackId, Long documentId, Integer pageNo);
}

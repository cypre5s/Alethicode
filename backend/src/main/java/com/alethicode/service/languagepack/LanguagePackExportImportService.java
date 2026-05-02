package com.alethicode.service.languagepack;

import java.util.Map;

public interface LanguagePackExportImportService {

    Map<String, Object> exportTask(Long taskId);

    Long importTask(Map<String, Object> payload, Long creatorId);

    Map<String, Object> deleteLanguagePack(Long languagePackId);
}

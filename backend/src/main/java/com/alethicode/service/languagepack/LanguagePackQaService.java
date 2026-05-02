package com.alethicode.service.languagepack;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface LanguagePackQaService {

    List<Map<String, Object>> listQaPacks(String username);

    Map<String, Object> createSession(String username, Long languagePackId);

    List<Map<String, Object>> listSessions(String username, Long languagePackId);

    List<Map<String, Object>> listMessages(String username, Long sessionId);

    Map<String, Object> sendMessage(String username, Long sessionId, String content);

    Map<String, Object> sendMessageAsync(String username, Long sessionId, String content);

    void submitFeedback(String username, Long messageId, String feedbackLabel, String comment);

    Map<String, Object> getCitationPage(String username, Long languagePackId, Long documentId, Integer pageNo);

    Path getPreviewDocumentPath(String username, Long languagePackId, Long documentId);

    void deleteSession(String username, Long sessionId);

    Map<String, Object> toggleSessionStarred(String username, Long sessionId);
}

package com.alethicode.service.languagepack;

public interface ConversationContextService {

    String buildRecentContext(Long sessionId);

    SessionContext buildSessionContext(Long sessionId);
}

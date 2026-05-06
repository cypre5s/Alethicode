package com.alethicode.service.languagepack;

import com.alethicode.service.aitutor.SessionUsage;

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

    /**
     * Phase 1 chat composer plan 1.7 节定义：读 language_pack_chat_session 三列拼成
     * {@link SessionUsage}，给前端 ContextUsageBar 与后续 RAG / answer-synthesis 调用方使用。
     *
     * <p>鉴权：sessionId 必须属于当前 username 的 chat session，否则抛
     * {@link com.alethicode.exception.BadRequestException}。</p>
     */
    SessionUsage getSessionUsage(String username, Long sessionId);

    /**
     * 压缩会话历史：用 LLM 摘要替换最近 K 条之前的旧消息。
     *
     * @param username  当前用户名（鉴权）
     * @param sessionId 会话 ID
     * @return compacted=true 时含 removed_count；消息不足时 compacted=false
     */
    Map<String, Object> compactSession(String username, Long sessionId);

    /**
     * 分叉会话：复制源会话的消息到新会话。
     *
     * @param username      当前用户名（鉴权）
     * @param sessionId     源会话 ID
     * @param fromMessageId 截止复制的消息 ID（null 表示复制全部）
     * @return 新会话信息含 session_id、language_pack_id、forked_from
     */
    Map<String, Object> forkSession(String username, Long sessionId, Long fromMessageId);
}

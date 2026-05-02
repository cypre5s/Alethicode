package com.alethicode.service.languagepack;

import java.util.Map;

public interface VideoJobService {

    Map<String, Object> createOrReuse(String username, Long messageId);

    Map<String, Object> getJob(String username, Long jobId);

    Map<String, Object> getJobByMessageId(Long messageId);
}

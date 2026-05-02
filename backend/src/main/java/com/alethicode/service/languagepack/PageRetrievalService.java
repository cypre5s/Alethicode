package com.alethicode.service.languagepack;

import java.util.List;

public interface PageRetrievalService {

    List<PageRetrievalHit> retrieve(Long languagePackId, String queryText, String recentContext);

    RetrievalTrace retrieveWithTrace(Long languagePackId, String queryText, String recentContext);
}

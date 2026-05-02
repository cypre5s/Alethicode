package com.alethicode.service.languagepack;

import java.util.List;

public interface AnswerSynthesisService {

    GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits);

    GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits, Long languagePackId);

    GroundedAnswer synthesizeAnswer(String question, List<PageRetrievalHit> hits, Long languagePackId, String primaryLanguage);

    SynthesisTrace synthesizeWithTrace(String question, List<PageRetrievalHit> hits, Long languagePackId);

    SynthesisTrace synthesizeWithTrace(String question, List<PageRetrievalHit> hits, Long languagePackId, String primaryLanguage);
}

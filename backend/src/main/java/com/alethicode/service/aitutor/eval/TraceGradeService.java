package com.alethicode.service.aitutor.eval;

import com.alethicode.service.aitutor.evidence.EvidencePack;
import com.alethicode.service.aitutor.policy.TutorActionDecision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TraceGradeService {

    public Map<String, Object> grade(
            EvidencePack evidencePack,
            Map<String, Object> guardrail,
            boolean schemaPass,
            TutorActionDecision actionDecision
    ) {
        String phase = String.valueOf(evidencePack.workflow().getOrDefault("phase", ""));
        boolean guardrailPassed = Boolean.TRUE.equals(guardrail.get("passed"));
        int weakKcCount = 0;
        Object weakKcs = evidencePack.learnerLongTerm().get("weak_kcs");
        if (weakKcs instanceof List<?> list) {
            weakKcCount = list.size();
        }
        double helpfulness = actionDecision.recommendedAction() == null || actionDecision.recommendedAction().isBlank() ? 0.0 : 0.70;
        if ("problem_guide".equals(actionDecision.recommendedAction()) && "READING".equals(phase)) {
            helpfulness += 0.15;
        }
        if ("re_ideate".equals(actionDecision.recommendedAction()) && weakKcCount > 0) {
            helpfulness += 0.10;
        }
        if ("ac_review".equals(actionDecision.recommendedAction()) && weakKcCount > 0) {
            helpfulness += 0.10;
        }
        if ("transfer".equals(actionDecision.recommendedAction()) && weakKcCount > 0) {
            helpfulness -= 0.25;
        }
        helpfulness = Math.max(0.0, Math.min(1.0, helpfulness));
        boolean pedagogyPass = guardrailPassed && helpfulness >= 0.55;
        double answerLeak = guardrailPassed ? 0.0 : 1.0;
        int hitCount = ((Number) evidencePack.retrieval().getOrDefault("hit_count", 0)).intValue();
        boolean contextExpected = "READING".equals(phase) || "IDEATING".equals(phase) || "AC_REVIEW".equals(phase);
        double contextRecall = hitCount > 0 ? 1.0 : (contextExpected ? 0.0 : 1.0);
        double contextPrecision = hitCount <= 0 ? 1.0 : (hitCount <= 3 ? 1.0 : 0.75);

        Map<String, Object> grade = new LinkedHashMap<>();
        grade.put("schema_pass", schemaPass);
        grade.put("pedagogy_pass", pedagogyPass);
        grade.put("helpfulness", helpfulness);
        grade.put("answer_leak", answerLeak);
        grade.put("context_recall", contextRecall);
        grade.put("context_precision", contextPrecision);
        grade.put("latency", 1.0);
        grade.put("stability", schemaPass && guardrailPassed ? 1.0 : 0.0);
        return grade;
    }
}

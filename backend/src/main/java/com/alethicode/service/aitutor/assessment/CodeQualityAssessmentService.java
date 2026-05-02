package com.alethicode.service.aitutor.assessment;

import java.util.Map;

public interface CodeQualityAssessmentService {

    Map<String, Object> assess(String code, String language, String problemDescription);
}

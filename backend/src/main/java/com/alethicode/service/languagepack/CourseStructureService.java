package com.alethicode.service.languagepack;

import java.util.Map;

public interface CourseStructureService {
    Map<String, Object> getCourseStructure(Long languagePackId);
    Map<String, Object> getKcGraph(Long languagePackId);
}

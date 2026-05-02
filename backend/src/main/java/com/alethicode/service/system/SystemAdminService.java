package com.alethicode.service.system;

import com.alethicode.dto.response.OrphanTestCaseResponse;

import java.util.List;

public interface SystemAdminService {

    List<OrphanTestCaseResponse> getOrphanTestCases();

    void deleteOrphanTestCase(String testCaseId);

    void deleteAllOrphanTestCases();
}

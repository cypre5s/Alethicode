package com.alethicode.controller;

import com.alethicode.service.nfk.NfkDataExportService;
import com.alethicode.service.aitutor.observability.AgentObservabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMethodSecurityContractTest extends AbstractControllerContractTest {

    @MockBean
    private AgentObservabilityService agentObservabilityService;

    @MockBean
    private NfkDataExportService nfkDataExportService;

    @Test
    void adminRoleCanAccessAiObservabilityEndpoints() throws Exception {
        when(agentObservabilityService.getAgentsOverview("7d"))
                .thenReturn(Map.of("range", "7d"));

        mockMvc.perform(get("/api/admin/ai/agents/overview")
                        .param("range", "7d")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.range").value("7d"));
    }

    @Test
    void adminRoleCanAccessCourseInsightEndpoints() throws Exception {
        when(jdbcTemplate.queryForList("SELECT id, name FROM classroom ORDER BY name"))
                .thenReturn(List.of(Map.of("id", "class-1", "name", "Python 入门班")));

        mockMvc.perform(get("/api/admin/insight/classrooms")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data[0].id").value("class-1"));
    }

    @Test
    void adminRoleCanAccessNfkEndpoints() throws Exception {
        when(nfkDataExportService.computeReadiness(1L))
                .thenReturn(Map.of("ready", true));

        mockMvc.perform(get("/api/admin/nfk/training-data/readiness")
                        .param("language_pack_id", "1")
                        .with(user("root").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.ready").value(true));
    }
}

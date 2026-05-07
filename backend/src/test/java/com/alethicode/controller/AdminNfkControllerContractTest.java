package com.alethicode.controller;

import com.alethicode.service.nfk.NfkDataExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNfkControllerContractTest {

    private NfkDataExportService exportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        exportService = mock(NfkDataExportService.class);
        AdminNfkController controller = new AdminNfkController(exportService);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    void exportEndpointStreamsCsvWithAttachmentHeader() throws Exception {
        StreamingResponseBody body = outputStream -> {
            outputStream.write(NfkDataExportService.CSV_HEADER.getBytes());
            outputStream.write('\n');
            outputStream.write("1,100,7,1,2026-04-10T10:00:00Z\n".getBytes());
        };
        when(exportService.exportTrainingData(eq(99L))).thenReturn(body);
        MvcResult initial = mockMvc.perform(get("/api/admin/nfk/training-data/export")
                        .param("language_pack_id", "99"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"nfk_training_pack_99.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv;charset=UTF-8"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).startsWith(NfkDataExportService.CSV_HEADER);
        assertThat(responseBody).contains("1,100,7,1,2026-04-10T10:00:00Z");
    }

    @Test
    void readinessEndpointReturnsReadinessSnapshot() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language_pack_id", 1L);
        payload.put("student_count", 30L);
        payload.put("problem_count", 50L);
        payload.put("covered_problem_count", 40L);
        payload.put("kc_count", 10L);
        payload.put("interaction_count", 900L);
        payload.put("kc_coverage", 0.8);
        payload.put("readiness_level", "HOT");
        payload.put("next_action", "HOT 路径文案");
        when(exportService.computeReadiness(anyLong())).thenReturn(payload);

        mockMvc.perform(get("/api/admin/nfk/training-data/readiness")
                        .param("language_pack_id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.data.language_pack_id").value(1))
                .andExpect(jsonPath("$.data.readiness_level").value("HOT"))
                .andExpect(jsonPath("$.data.kc_coverage").value(0.8))
                .andExpect(jsonPath("$.data.next_action").isString());
    }
}

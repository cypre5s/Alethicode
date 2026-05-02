package com.alethicode.exception;

import com.alethicode.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionHarnessController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldMapBusinessException() throws Exception {
        mockMvc.perform(get("/harness/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not-found"))
                .andExpect(jsonPath("$.data").value("KC not found"));
    }

    @Test
    void shouldMapBadRequestExceptions() throws Exception {
        mockMvc.perform(get("/harness/bad-request").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"))
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    void shouldMapUnhandledExceptionAsInternalError() throws Exception {
        mockMvc.perform(get("/harness/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal-error"))
                .andExpect(jsonPath("$.data").value("Internal server error"));
    }

    @RestController
    @RequestMapping("/harness")
    static class ExceptionHarnessController {

        @GetMapping("/not-found")
        public ApiResponse<String> notFound() {
            throw new BusinessException(ErrorCode.NOT_FOUND, "KC not found");
        }

        @GetMapping("/bad-request")
        public ApiResponse<String> badRequest(@RequestParam("count") int count) {
            return ApiResponse.success(String.valueOf(count));
        }

        @GetMapping("/crash")
        public ApiResponse<String> crash() {
            throw new IllegalStateException("boom");
        }
    }
}

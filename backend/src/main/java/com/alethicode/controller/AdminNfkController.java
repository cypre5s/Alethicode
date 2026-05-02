package com.alethicode.controller;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.nfk.NfkDataExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * NFK 训练数据导出与课程包数据就绪度查询。
 *
 * <p>挂在 {@code /api/admin/nfk/}，仅管理员可用（鉴权由统一 {@code SessionAuthenticationFilter}
 * 在上游处理）。本 controller 只负责参数解析 + 结果编排。
 */
@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminNfkController {

    private final NfkDataExportService nfkDataExportService;

    public AdminNfkController(NfkDataExportService nfkDataExportService) {
        this.nfkDataExportService = nfkDataExportService;
    }

    /**
     * 下载指定课程包的 NFK 训练 CSV。
     *
     * <p>Response：
     * <ul>
     *   <li>{@code Content-Type: text/csv;charset=UTF-8}；</li>
     *   <li>{@code Content-Disposition: attachment; filename="nfk_training_pack_{id}.csv"}；</li>
     *   <li>CSV 首行固定为 {@code user_id,question_id,skill_id,response,timestamp}。</li>
     * </ul>
     */
    @GetMapping(value = "/api/admin/nfk/training-data/export",
            produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> exportTrainingData(
            Authentication auth,
            @RequestParam("language_pack_id") Long languagePackId) {
        StreamingResponseBody body = nfkDataExportService.exportTrainingData(languagePackId);
        String filename = "nfk_training_pack_" + languagePackId + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /**
     * 查询课程包的 NFK 数据就绪度快照。
     */
    @GetMapping("/api/admin/nfk/training-data/readiness")
    public ApiResponse<Map<String, Object>> readiness(
            Authentication auth,
            @RequestParam("language_pack_id") Long languagePackId) {
        return ApiResponse.success(nfkDataExportService.computeReadiness(languagePackId));
    }
}

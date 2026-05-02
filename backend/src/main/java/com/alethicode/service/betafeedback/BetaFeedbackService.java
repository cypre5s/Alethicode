package com.alethicode.service.betafeedback;

import com.alethicode.dto.request.BetaFeedbackCreateRequest;
import com.alethicode.dto.request.BetaTelemetryBatchRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 公测反馈与遥测的学生侧服务。所有方法走 fail-fast 校验：
 *   - 业务非法输入（type/severity/截图大小或类型）抛 BadRequestException → 422
 *   - SMTP 发送失败不阻塞主流程，把 mail_status=failed 写回库
 */
public interface BetaFeedbackService {

    /**
     * 创建反馈主记录与可选附件，触发异步邮件通知。
     *
     * @param request    反馈表单
     * @param screenshots 可选截图（最多 3 张，单张 ≤ 5 MB，仅 png/jpeg/webp）
     * @param userId     提交者 user.id；不能为空
     * @return 新建反馈 id
     */
    long createReport(BetaFeedbackCreateRequest request, MultipartFile[] screenshots, Long userId);

    /**
     * 批量写入前端遥测事件。userId 可为 null（匿名 web vital 等）。
     */
    void recordTelemetryEvents(List<BetaTelemetryBatchRequest.TelemetryEvent> events, Long userId);

    /**
     * 单条 Web Vital。userId 可为 null。
     */
    void recordWebVital(
            String metric,
            double value,
            String rating,
            String navigationType,
            String route,
            Long userId
    );
}

package com.alethicode.controller;

import com.alethicode.config.TemporalLanguagePackWorkflowConfig.LanguagePackTemporalWorkerLauncher;
import com.alethicode.dto.response.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员探测 Temporal 集成运行状态的 REST 端点。
 *
 * 2C4G 部署下 Temporal 容器是按需启动（profile=temporal）：
 *   - 容器未启动时，backend 后台轮询会失败，isWorkerRunning() = false
 *   - 管理员跑 scripts/temporal-on.sh 启动容器后，backend 60 秒内自动注册 worker，
 *     isWorkerRunning() 翻成 true，前端 admin 面板可据此放开"启动流水线"按钮。
 *
 * 用 ObjectProvider 而非直接注入 LanguagePackTemporalWorkerLauncher：
 * 后者带 @ConditionalOnProperty(alethicode.temporal.enabled=true)，
 * 当配置关闭时 bean 不存在，直接注入会让 controller 启动失败。
 */
@RestController
@RequestMapping("/api/admin/temporal")
public class AdminTemporalStatusController {

    private final ObjectProvider<LanguagePackTemporalWorkerLauncher> launcherProvider;

    public AdminTemporalStatusController(ObjectProvider<LanguagePackTemporalWorkerLauncher> launcherProvider) {
        this.launcherProvider = launcherProvider;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> status() {
        LanguagePackTemporalWorkerLauncher launcher = launcherProvider.getIfAvailable();
        boolean enabled = launcher != null;
        boolean running = launcher != null && launcher.isWorkerRunning();
        String hint;
        if (!enabled) {
            hint = "Temporal 集成未启用（alethicode.temporal.enabled=false）";
        } else if (!running) {
            hint = "Temporal 容器未启动。SSH 到云主机执行：bash scripts/temporal-on.sh";
        } else {
            hint = "Temporal 已就绪，可启动课件流水线";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", enabled);
        body.put("running", running);
        body.put("hint", hint);
        return ApiResponse.success(body);
    }
}

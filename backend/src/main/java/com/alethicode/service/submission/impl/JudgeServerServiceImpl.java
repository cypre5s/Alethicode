package com.alethicode.service.submission.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.JudgeServerHeartbeatRequest;
import com.alethicode.dto.response.JudgeServerItemResponse;
import com.alethicode.dto.response.JudgeServerListResponse;
import com.alethicode.entity.JudgeServer;
import com.alethicode.repository.JudgeServerRepository;
import com.alethicode.service.submission.JudgeServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class JudgeServerServiceImpl implements JudgeServerService {

    private final JudgeServerRepository judgeServerRepository;
    private final AlethicodeProperties properties;
    private final Clock clock;

    @Autowired
    public JudgeServerServiceImpl(
            JudgeServerRepository judgeServerRepository,
            AlethicodeProperties properties
    ) {
        this(judgeServerRepository, properties, Clock.systemUTC());
    }

    JudgeServerServiceImpl(
            JudgeServerRepository judgeServerRepository,
            AlethicodeProperties properties,
            Clock clock
    ) {
        this.judgeServerRepository = judgeServerRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void handleHeartbeat(JudgeServerHeartbeatRequest request, String ipAddress) {
        Instant now = Instant.now(clock);
        JudgeServer judgeServer = judgeServerRepository.findByHostname(request.hostname())
                .orElseGet(JudgeServer::new);

        if (judgeServer.getCreateTime() == null) {
            judgeServer.setCreateTime(now);
            judgeServer.setTaskNumber(0);
        }

        judgeServer.setHostname(request.hostname());
        judgeServer.setJudgerVersion(request.judgerVersion());
        judgeServer.setCpuCore(request.cpuCore());
        judgeServer.setMemoryUsage(request.memory());
        judgeServer.setCpuUsage(request.cpu());
        judgeServer.setIp(ipAddress);
        judgeServer.setServiceUrl(request.serviceUrl());
        judgeServer.setLastHeartbeat(now);

        judgeServerRepository.save(judgeServer);
    }

    @Override
    public JudgeServerListResponse getActiveJudgeServers() {
        Instant cutoff = Instant.now(clock).minusSeconds(600);
        List<JudgeServerItemResponse> servers = judgeServerRepository
                .findByLastHeartbeatGreaterThanEqualOrderByLastHeartbeatDesc(cutoff)
                .stream()
                .filter(server -> "normal".equals(server.getStatus(clock)))
                .map(server -> new JudgeServerItemResponse(
                        server.getId(),
                        server.getHostname(),
                        server.getIp(),
                        server.getJudgerVersion(),
                        server.getCpuCore(),
                        server.getMemoryUsage(),
                        server.getCpuUsage(),
                        server.getLastHeartbeat(),
                        server.getCreateTime(),
                        server.getTaskNumber(),
                        server.getServiceUrl(),
                        server.isDisabled(),
                        server.getStatus(clock)
                ))
                .toList();

        String rawToken = properties.getJudgeServer().getToken();
        String maskedToken = rawToken.length() > 8
                ? rawToken.substring(0, 4) + "****" + rawToken.substring(rawToken.length() - 4)
                : "****";
        return new JudgeServerListResponse(maskedToken, servers);
    }

    @Override
    public void deleteJudgeServer(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return;
        }
        judgeServerRepository.deleteByHostname(hostname);
    }

    @Override
    public void updateJudgeServer(Long id, boolean isDisabled) {
        JudgeServer judgeServer = judgeServerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Judge server not found"));
        judgeServer.setDisabled(isDisabled);
        judgeServerRepository.save(judgeServer);
    }
}

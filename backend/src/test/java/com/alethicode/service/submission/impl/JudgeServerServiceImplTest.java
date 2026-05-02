package com.alethicode.service.submission.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.JudgeServerHeartbeatRequest;
import com.alethicode.dto.response.JudgeServerListResponse;
import com.alethicode.entity.JudgeServer;
import com.alethicode.repository.JudgeServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServerServiceImplTest {

    @Mock
    private JudgeServerRepository judgeServerRepository;

    @Test
    void handleHeartbeatShouldCreateJudgeServerWhenHostnameIsNew() {
        AlethicodeProperties properties = new AlethicodeProperties();
        Instant now = Instant.parse("2026-03-25T08:00:00Z");
        JudgeServerServiceImpl service = new JudgeServerServiceImpl(
                judgeServerRepository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(judgeServerRepository.findByHostname("judge-1")).thenReturn(Optional.empty());

        service.handleHeartbeat(
                new JudgeServerHeartbeatRequest("judge-1", "1.6.1", 4, 12.5, 8.5, "heartbeat", "http://judge:8080"),
                "127.0.0.1"
        );

        ArgumentCaptor<JudgeServer> captor = ArgumentCaptor.forClass(JudgeServer.class);
        verify(judgeServerRepository).save(captor.capture());
        JudgeServer saved = captor.getValue();
        assertThat(saved.getHostname()).isEqualTo("judge-1");
        assertThat(saved.getJudgerVersion()).isEqualTo("1.6.1");
        assertThat(saved.getCpuCore()).isEqualTo(4);
        assertThat(saved.getMemoryUsage()).isEqualTo(12.5);
        assertThat(saved.getCpuUsage()).isEqualTo(8.5);
        assertThat(saved.getIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getServiceUrl()).isEqualTo("http://judge:8080");
        assertThat(saved.getTaskNumber()).isEqualTo(0);
        assertThat(saved.getCreateTime()).isEqualTo(now);
        assertThat(saved.getLastHeartbeat()).isEqualTo(now);
    }

    @Test
    void getActiveJudgeServersShouldReturnWrappedJudgeServerPayload() {
        AlethicodeProperties properties = new AlethicodeProperties();
        properties.getJudgeServer().setToken("judge-token-for-test");
        Instant now = Instant.parse("2026-03-25T08:00:00Z");
        JudgeServerServiceImpl service = new JudgeServerServiceImpl(
                judgeServerRepository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        JudgeServer active = new JudgeServer();
        active.setId(1L);
        active.setHostname("judge-1");
        active.setIp("127.0.0.1");
        active.setJudgerVersion("1.6.1");
        active.setCpuCore(4);
        active.setMemoryUsage(12.5);
        active.setCpuUsage(8.5);
        active.setCreateTime(now.minusSeconds(3600));
        active.setLastHeartbeat(now.minusSeconds(5));
        active.setTaskNumber(2);
        active.setServiceUrl("http://judge:8080");
        active.setDisabled(false);

        JudgeServer abnormal = new JudgeServer();
        abnormal.setId(2L);
        abnormal.setHostname("judge-2");
        abnormal.setIp("127.0.0.2");
        abnormal.setJudgerVersion("1.6.1");
        abnormal.setCpuCore(4);
        abnormal.setMemoryUsage(20.0);
        abnormal.setCpuUsage(9.5);
        abnormal.setCreateTime(now.minusSeconds(3600));
        abnormal.setLastHeartbeat(now.minusSeconds(20));
        abnormal.setTaskNumber(0);
        abnormal.setServiceUrl("http://judge-2:8080");
        abnormal.setDisabled(false);

        when(judgeServerRepository.findByLastHeartbeatGreaterThanEqualOrderByLastHeartbeatDesc(now.minusSeconds(600)))
                .thenReturn(List.of(active, abnormal));

        JudgeServerListResponse response = service.getActiveJudgeServers();

        assertThat(response.token()).isEqualTo("judg****test");
        assertThat(response.servers()).hasSize(1);
        assertThat(response.servers().getFirst().hostname()).isEqualTo("judge-1");
        assertThat(response.servers().getFirst().status()).isEqualTo("normal");
        assertThat(response.servers().getFirst().taskNumber()).isEqualTo(2);
        assertThat(response.servers().getFirst().isDisabled()).isFalse();
    }

    @Test
    void deleteAndUpdateJudgeServerShouldPersistManagementChanges() {
        AlethicodeProperties properties = new AlethicodeProperties();
        JudgeServerServiceImpl service = new JudgeServerServiceImpl(
                judgeServerRepository,
                properties,
                Clock.systemUTC()
        );

        JudgeServer judgeServer = new JudgeServer();
        judgeServer.setId(1L);
        judgeServer.setHostname("judge-1");
        judgeServer.setDisabled(false);

        when(judgeServerRepository.findById(1L)).thenReturn(Optional.of(judgeServer));

        service.deleteJudgeServer("judge-1");
        service.updateJudgeServer(1L, true);

        verify(judgeServerRepository).deleteByHostname("judge-1");
        verify(judgeServerRepository).save(judgeServer);
        assertThat(judgeServer.isDisabled()).isTrue();
    }
}

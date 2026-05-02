package com.alethicode.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "judge_server")
public class JudgeServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String hostname;

    private String ip;

    @Column(name = "judger_version", nullable = false)
    private String judgerVersion;

    @Column(name = "cpu_core", nullable = false)
    private Integer cpuCore;

    @Column(name = "memory_usage", nullable = false)
    private Double memoryUsage;

    @Column(name = "cpu_usage", nullable = false)
    private Double cpuUsage;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "create_time", nullable = false)
    private Instant createTime;

    @Column(name = "task_number", nullable = false)
    private Integer taskNumber = 0;

    @Column(name = "service_url")
    private String serviceUrl;

    @Column(name = "is_disabled", nullable = false)
    private boolean disabled;

    @Column(name = "agent_version")
    private String agentVersion;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "heartbeat_lag_seconds")
    private Double heartbeatLagSeconds = 0.0;

    @Column(name = "available_slots")
    private Integer availableSlots = 0;

    @Column(name = "running_tasks")
    private Integer runningTasks = 0;

    @Column(name = "queued_tasks")
    private Integer queuedTasks = 0;

    @Column(name = "filesystem_usage_ratio")
    private Double filesystemUsageRatio = 0.0;

    @Column(name = "cgroup_cpu_throttled_ratio")
    private Double cgroupCpuThrottledRatio = 0.0;

    @Column(name = "queue_wait_p95_seconds")
    private Double queueWaitP95Seconds = 0.0;

    @Column(name = "end_to_end_p95_seconds")
    private Double endToEndP95Seconds = 0.0;

    @Column(name = "security_incident_total_1h")
    private Integer securityIncidentTotal1h = 0;

    public String getStatus(Clock clock) {
        if (lastHeartbeat == null) {
            return "abnormal";
        }
        return Instant.now(clock).minusSeconds(6).isAfter(lastHeartbeat) ? "abnormal" : "normal";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getJudgerVersion() {
        return judgerVersion;
    }

    public void setJudgerVersion(String judgerVersion) {
        this.judgerVersion = judgerVersion;
    }

    public Integer getCpuCore() {
        return cpuCore;
    }

    public void setCpuCore(Integer cpuCore) {
        this.cpuCore = cpuCore;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Integer getTaskNumber() {
        return taskNumber;
    }

    public void setTaskNumber(Integer taskNumber) {
        this.taskNumber = taskNumber;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public Double getHeartbeatLagSeconds() { return heartbeatLagSeconds; }
    public void setHeartbeatLagSeconds(Double heartbeatLagSeconds) { this.heartbeatLagSeconds = heartbeatLagSeconds; }
    public Integer getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(Integer availableSlots) { this.availableSlots = availableSlots; }
    public Integer getRunningTasks() { return runningTasks; }
    public void setRunningTasks(Integer runningTasks) { this.runningTasks = runningTasks; }
    public Integer getQueuedTasks() { return queuedTasks; }
    public void setQueuedTasks(Integer queuedTasks) { this.queuedTasks = queuedTasks; }
    public Double getFilesystemUsageRatio() { return filesystemUsageRatio; }
    public void setFilesystemUsageRatio(Double filesystemUsageRatio) { this.filesystemUsageRatio = filesystemUsageRatio; }
    public Double getCgroupCpuThrottledRatio() { return cgroupCpuThrottledRatio; }
    public void setCgroupCpuThrottledRatio(Double cgroupCpuThrottledRatio) { this.cgroupCpuThrottledRatio = cgroupCpuThrottledRatio; }
    public Double getQueueWaitP95Seconds() { return queueWaitP95Seconds; }
    public void setQueueWaitP95Seconds(Double queueWaitP95Seconds) { this.queueWaitP95Seconds = queueWaitP95Seconds; }
    public Double getEndToEndP95Seconds() { return endToEndP95Seconds; }
    public void setEndToEndP95Seconds(Double endToEndP95Seconds) { this.endToEndP95Seconds = endToEndP95Seconds; }
    public Integer getSecurityIncidentTotal1h() { return securityIncidentTotal1h; }
    public void setSecurityIncidentTotal1h(Integer securityIncidentTotal1h) { this.securityIncidentTotal1h = securityIncidentTotal1h; }
}

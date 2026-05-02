package com.alethicode.repository;

import com.alethicode.entity.JudgeServer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JudgeServerRepository extends JpaRepository<JudgeServer, Long> {

    Optional<JudgeServer> findByHostname(String hostname);

    List<JudgeServer> findByLastHeartbeatGreaterThanEqualOrderByLastHeartbeatDesc(Instant cutoff);

    void deleteByHostname(String hostname);
}

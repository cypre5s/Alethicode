package com.alethicode.service.twin.kc;

import com.alethicode.dto.response.twin.KcGalaxyResponse;
import com.alethicode.dto.response.twin.KcGalaxyResponse.KcGalaxyEdge;
import com.alethicode.dto.response.twin.KcGalaxyResponse.KcGalaxyNode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KcGalaxyProjectorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final KcGalaxyProjector projector = new KcGalaxyProjector(jdbcTemplate);

    @Test
    void projectReturnsEmptyForNoKcs() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        KcGalaxyResponse result = projector.project(42L, 1L);
        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
    }

    @Test
    void projectReturnsNodesWithMastery() {
        KcGalaxyNode node = new KcGalaxyNode(1L, "for 循环", 0.75, Instant.now(), 3, "循环");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(node))
                .thenReturn(List.of());

        KcGalaxyResponse result = projector.project(42L, 1L);
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().get(0).name()).isEqualTo("for 循环");
        assertThat(result.nodes().get(0).mastery()).isEqualTo(0.75);
    }

    @Test
    void projectReturnsEdges() {
        KcGalaxyNode node1 = new KcGalaxyNode(1L, "变量", 0.9, null, 0, "基础");
        KcGalaxyNode node2 = new KcGalaxyNode(2L, "循环", 0.5, null, 1, "控制流");
        KcGalaxyEdge edge = new KcGalaxyEdge(1L, 2L, "prerequisite", 1.0);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(node1, node2))
                .thenReturn(List.of(edge));

        KcGalaxyResponse result = projector.project(42L, 1L);
        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().get(0).relationType()).isEqualTo("prerequisite");
    }

    @Test
    void projectWithoutLanguagePackCrossesAllPacks() {
        KcGalaxyNode node = new KcGalaxyNode(1L, "函数", 0.6, null, 0, "");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(node))
                .thenReturn(List.of());

        KcGalaxyResponse result = projector.project(42L, null);
        assertThat(result.nodes()).hasSize(1);
    }

    @Test
    void masteryBoundaryZero() {
        KcGalaxyNode node = new KcGalaxyNode(1L, "递归", 0.0, null, 0, "高级");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(node))
                .thenReturn(List.of());

        KcGalaxyResponse result = projector.project(42L, 1L);
        assertThat(result.nodes().get(0).mastery()).isEqualTo(0.0);
    }

    @Test
    void masteryBoundaryOne() {
        KcGalaxyNode node = new KcGalaxyNode(1L, "变量赋值", 1.0, Instant.now(), 5, "基础");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(node))
                .thenReturn(List.of());

        KcGalaxyResponse result = projector.project(42L, 1L);
        assertThat(result.nodes().get(0).mastery()).isEqualTo(1.0);
    }
}

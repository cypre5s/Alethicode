package com.alethicode.service.twin.museum;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorMuseumServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ErrorMuseumService service = new ErrorMuseumService(jdbcTemplate);

    @Test
    void listPinsReturnsEmptyForNewUser() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenReturn(List.of());
        assertThat(service.listPins(42L)).isEmpty();
    }

    @Test
    void pinMemoryRejectsOtherUserMemory() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(100L)))
                .thenReturn(999L);

        assertThatThrownBy(() -> service.pinMemory(42L, 100L, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memory-not-yours");
    }

    @Test
    void pinMemoryRejectsWhenMaxPinsReached() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(100L)))
                .thenReturn(42L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L)))
                .thenReturn(9);

        assertThatThrownBy(() -> service.pinMemory(42L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("max-pins-reached");
    }

    @Test
    void unpinMemoryThrowsForNonExistentPin() {
        when(jdbcTemplate.update(anyString(), eq(999L), eq(42L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.unpinMemory(42L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pin-not-found");
    }

    @Test
    void updatePinThrowsForNonExistentPin() {
        when(jdbcTemplate.update(anyString(), any(), any(), eq(999L), eq(42L)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updatePin(42L, 999L, "new annotation", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pin-not-found");
    }
}

package com.alethicode.controller;

import com.alethicode.dto.request.OverrideProfileSummaryRequest;
import com.alethicode.dto.request.UpdateProfilePreferencesRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.dto.response.StudentProfileView;
import com.alethicode.service.aitutor.profile.ProfileViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileControllerTest {

    private ProfileViewService profileViewService;
    private ProfileController controller;

    @BeforeEach
    void setUp() {
        profileViewService = mock(ProfileViewService.class);
        controller = new ProfileController(profileViewService);
    }

    @Test
    void getMyProfile_returnsViewForAuthenticatedUser() {
        StudentProfileView view = sampleProfile(42L);
        when(profileViewService.getMyProfile(42L)).thenReturn(view);

        ApiResponse<StudentProfileView> response = controller.getMyProfile(authFor(42L));

        assertThat(response.error()).isNull();
        assertThat(response.data()).isEqualTo(view);
    }

    @Test
    void getMyProfile_unauthorizedRequestFailsFast() {
        assertThatThrownBy(() -> controller.getMyProfile(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(profileViewService, never()).getMyProfile(any());
    }

    @Test
    void updatePreferences_persistsAndEchoesFlag() {
        ApiResponse<Map<String, Object>> response = controller.updatePreferences(
                new UpdateProfilePreferencesRequest(false),
                authFor(42L)
        );

        verify(profileViewService).updatePreferences(42L, false);
        assertThat(response.error()).isNull();
        assertThat(response.data()).containsEntry("ok", true);
        assertThat(response.data()).containsEntry("personalization_enabled", false);
    }

    @Test
    void refreshSummary_triggersServiceAndReturnsLatestVersion() {
        StudentProfileView view = sampleProfile(42L);
        when(profileViewService.getMyProfile(42L)).thenReturn(view);

        ApiResponse<Map<String, Object>> response = controller.refreshSummary(authFor(42L));

        verify(profileViewService).refreshSummary(42L);
        verify(profileViewService, times(1)).getMyProfile(42L);
        assertThat(response.data()).containsEntry("ok", true);
        assertThat(response.data()).containsEntry("version", view.narrativeSummary().version());
        assertThat(response.data()).containsEntry("summary_text", view.narrativeSummary().text());
    }

    @Test
    void overrideSummary_persistsTextAndReturnsBumpedVersion() {
        StudentProfileView view = sampleProfile(42L);
        when(profileViewService.getMyProfile(42L)).thenReturn(view);

        ApiResponse<Map<String, Object>> response = controller.overrideSummary(
                new OverrideProfileSummaryRequest("我自己写的画像"),
                authFor(42L)
        );

        verify(profileViewService).overrideSummary(42L, "我自己写的画像");
        assertThat(response.data()).containsEntry("ok", true);
        assertThat(response.data()).containsEntry("version", view.narrativeSummary().version());
    }

    @Test
    void anonymousAccessIsRejectedAcrossAllProfileEndpoints() {
        Authentication anonymous = null;

        assertThatThrownBy(() -> controller.refreshSummary(anonymous))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.updatePreferences(
                new UpdateProfilePreferencesRequest(true), anonymous))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.overrideSummary(
                new OverrideProfileSummaryRequest("x"), anonymous))
                .isInstanceOf(ResponseStatusException.class);
        verify(profileViewService, never()).updatePreferences(any(), anyBoolean());
        verify(profileViewService, never()).refreshSummary(any());
        verify(profileViewService, never()).overrideSummary(any(), any());
    }

    private static StudentProfileView sampleProfile(Long userId) {
        return new StudentProfileView(
                userId,
                true,
                false,
                new StudentProfileView.NarrativeSummaryView(
                        3,
                        "近 30 天 AC 12 道",
                        Instant.parse("2026-04-26T00:00:00Z"),
                        true
                ),
                new StudentProfileView.LearningStyleView("step_by_step", "step-by-step"),
                List.of(new StudentProfileView.KcView("for_loop", 0.45)),
                List.of(),
                List.of(),
                Map.of("problems_attempted_30d", 18L, "problems_ac_30d", 12L)
        );
    }

    private static Authentication authFor(Long userId) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken("user", "n/a", List.of());
        token.setDetails(userId);
        return token;
    }
}

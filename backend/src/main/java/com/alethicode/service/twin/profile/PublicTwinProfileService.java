package com.alethicode.service.twin.profile;

import com.alethicode.service.twin.kc.KcGalaxyProjector;
import com.alethicode.service.twin.museum.ErrorMuseumService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PublicTwinProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final KcGalaxyProjector kcGalaxyProjector;
    private final ErrorMuseumService museumService;

    public PublicTwinProfileService(JdbcTemplate jdbcTemplate,
                                     KcGalaxyProjector kcGalaxyProjector,
                                     ErrorMuseumService museumService) {
        this.jdbcTemplate = jdbcTemplate;
        this.kcGalaxyProjector = kcGalaxyProjector;
        this.museumService = museumService;
    }

    public Map<String, Object> getPublicProfile(String handle) {
        Map<String, Object> profile;
        try {
            profile = jdbcTemplate.queryForMap("""
                SELECT p.user_id, p.public_handle, p.privacy_level,
                       p.show_kc_galaxy, p.show_timeline, p.show_museum,
                       p.show_persona, p.show_insights, p.custom_bio, p.avatar_url
                FROM twin_public_profile p
                WHERE p.public_handle = ? AND p.privacy_level != 'private'
                """, handle);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }

        Long userId = ((Number) profile.get("user_id")).longValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handle", profile.get("public_handle"));
        result.put("bio", profile.get("custom_bio"));
        result.put("avatar_url", profile.get("avatar_url"));
        result.put("privacy_level", profile.get("privacy_level"));

        if (Boolean.TRUE.equals(profile.get("show_kc_galaxy"))) {
            result.put("kc_galaxy", kcGalaxyProjector.project(userId, null));
        }
        if (Boolean.TRUE.equals(profile.get("show_museum"))) {
            result.put("museum", museumService.listPins(userId));
        }
        if (Boolean.TRUE.equals(profile.get("show_persona"))) {
            try {
                String summary = jdbcTemplate.queryForObject(
                        "SELECT summary_text FROM ai_learner_narrative_summary WHERE user_id = ? AND user_disabled = FALSE",
                        String.class, userId);
                result.put("persona_text", summary);
            } catch (EmptyResultDataAccessException e) {
                result.put("persona_text", null);
            }
        }

        return result;
    }

    public void updatePrivacy(Long userId, Map<String, Object> settings) {
        jdbcTemplate.update("""
            INSERT INTO twin_public_profile (user_id, public_handle, privacy_level,
                show_kc_galaxy, show_timeline, show_museum, show_persona, show_insights, custom_bio)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id)
            DO UPDATE SET privacy_level = EXCLUDED.privacy_level,
                show_kc_galaxy = EXCLUDED.show_kc_galaxy,
                show_timeline = EXCLUDED.show_timeline,
                show_museum = EXCLUDED.show_museum,
                show_persona = EXCLUDED.show_persona,
                show_insights = EXCLUDED.show_insights,
                custom_bio = EXCLUDED.custom_bio,
                updated_at = NOW()
            """,
                userId,
                settings.getOrDefault("handle", "user_" + userId),
                settings.getOrDefault("privacy_level", "private"),
                settings.getOrDefault("show_kc_galaxy", true),
                settings.getOrDefault("show_timeline", false),
                settings.getOrDefault("show_museum", true),
                settings.getOrDefault("show_persona", true),
                settings.getOrDefault("show_insights", true),
                settings.getOrDefault("bio", null)
        );
    }
}

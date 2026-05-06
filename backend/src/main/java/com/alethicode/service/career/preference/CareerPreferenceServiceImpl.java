package com.alethicode.service.career.preference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CareerPreferenceServiceImpl implements CareerPreferenceService {

    public static final String MODULE_CAREER_BRIDGING = "career_bridging";
    public static final String MODULE_CODING_LENS = "coding_lens";
    public static final String MODULE_CAREER_STUDIO = "career_studio";
    public static final String MODULE_CAREER_PATH = "career_path";

    private static final Logger log = LoggerFactory.getLogger(CareerPreferenceServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public CareerPreferenceServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CareerPreferences findPreferences(long userId) {
        try {
            return jdbcTemplate.queryForObject("""
                    select
                        coalesce(career_bridging_disabled, false) as cb,
                        coalesce(coding_lens_disabled, false)     as cl,
                        coalesce(career_studio_disabled, false)   as cs,
                        coalesce(career_path_disabled, false)     as cp
                    from user_profile
                    where user_id = ?
                    """, (rs, rowNum) -> new CareerPreferences(
                            rs.getBoolean("cb"),
                            rs.getBoolean("cl"),
                            rs.getBoolean("cs"),
                            rs.getBoolean("cp")
                    ), userId);
        } catch (EmptyResultDataAccessException ignored) {
            return CareerPreferences.allEnabled();
        }
    }

    @Override
    @Transactional
    public void updatePreferences(long userId, CareerPreferences preferences) {
        if (preferences == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "preferences body is required");
        }
        int updated = jdbcTemplate.update("""
                update user_profile
                set career_bridging_disabled = ?,
                    coding_lens_disabled = ?,
                    career_studio_disabled = ?,
                    career_path_disabled = ?
                where user_id = ?
                """,
                preferences.careerBridgingDisabled(),
                preferences.codingLensDisabled(),
                preferences.careerStudioDisabled(),
                preferences.careerPathDisabled(),
                userId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "user_profile not found for user_id=" + userId);
        }
        log.info("career preferences updated: user={}, prefs={}", userId, preferences);
    }

    @Override
    public boolean isModuleDisabled(long userId, String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return false;
        }
        CareerPreferences prefs = findPreferences(userId);
        return switch (moduleName) {
            case MODULE_CAREER_BRIDGING -> prefs.careerBridgingDisabled();
            case MODULE_CODING_LENS -> prefs.codingLensDisabled();
            case MODULE_CAREER_STUDIO -> prefs.careerStudioDisabled();
            case MODULE_CAREER_PATH -> prefs.careerPathDisabled();
            default -> false;
        };
    }
}

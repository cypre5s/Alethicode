package com.alethicode.service.classroom.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAccessHelper;
import com.alethicode.service.classroom.ClassroomAccessHelper.UserAuth;
import com.alethicode.service.classroom.ClassroomCoreDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.alethicode.util.ServiceParseUtils.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomCoreDomainServiceImpl implements ClassroomCoreDomainService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClassroomAccessHelper access;
    private final Path lessonRoot;

    public ClassroomCoreDomainServiceImpl(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          AlethicodeProperties properties,
                                          ClassroomAccessHelper access) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.access = access;
        this.lessonRoot = Paths.get(properties.getSystem().getClassroomLessonDir());
    }

    @Override
    public ApiResponse<Object> classroomList(Map<String, String> params, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);

        int page = Math.max(parseInt(params.get("page"), 1), 1);
        int limit = Math.min(Math.max(parseInt(params.get("limit"), 20), 1), 100);
        int offset = (page - 1) * limit;

        String role = trimToNull(params.get("role"));
        List<Object> args = new ArrayList<>();
        String where = " where cm.user_id = ? and c.is_active = true";
        args.add(user.userId());
        if (role != null) {
            where += " and cm.role = ?";
            args.add(role);
        }

        Long total = jdbcTemplate.queryForObject(
                "select count(*) from classroom c join classroom_member cm on cm.classroom_id = c.id" + where,
                Long.class, args.toArray());

        args.add(limit);
        args.add(offset);
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                select c.id, c.name, c.description, c.course_code, c.semester,
                       c.is_active, c.allow_student_view_others, c.enable_ai_tutor, c.enable_collaboration,
                       coalesce(member_stats.member_count, 0) as member_count,
                       c.problem_count, c.lesson_count, c.current_chapter, c.chapter_unlock_threshold,
                       c.create_time, c.update_time, cm.role as my_role,
                       u.id as created_by_id, u.username as created_by_username,
                       lp.id as language_pack_id, lp.name as language_pack_name,
                       lp.version as language_pack_version, lp.primary_language as language_pack_primary_language
                from classroom c
                join classroom_member cm on cm.classroom_id = c.id
                join "user" u on u.id = c.created_by_id
                left join classroom_language_pack clp on clp.classroom_id = c.id
                left join language_pack lp on lp.id = clp.language_pack_id
                left join (
                    select classroom_id, count(*) as member_count
                    from classroom_member
                    group by classroom_id
                ) member_stats on member_stats.classroom_id = c.id
                """ + where + " order by c.create_time desc limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> createdBy = new LinkedHashMap<>();
                    createdBy.put("id", rs.getLong("created_by_id"));
                    createdBy.put("username", rs.getString("created_by_username"));
                    createdBy.put("avatar", null);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("name", rs.getString("name"));
                    row.put("description", rs.getString("description"));
                    row.put("course_code", rs.getString("course_code"));
                    row.put("semester", rs.getString("semester"));
                    row.put("created_by", createdBy);
                    row.put("is_active", rs.getBoolean("is_active"));
                    row.put("allow_student_view_others", rs.getBoolean("allow_student_view_others"));
                    row.put("enable_ai_tutor", rs.getBoolean("enable_ai_tutor"));
                    row.put("enable_collaboration", rs.getBoolean("enable_collaboration"));
                    row.put("member_count", rs.getInt("member_count"));
                    row.put("problem_count", rs.getInt("problem_count"));
                    row.put("lesson_count", rs.getInt("lesson_count"));
                    row.put("current_chapter", rs.getInt("current_chapter"));
                    row.put("chapter_unlock_threshold", rs.getDouble("chapter_unlock_threshold"));
                    row.put("create_time", formatTime(rs.getTimestamp("create_time")));
                    row.put("update_time", formatTime(rs.getTimestamp("update_time")));
                    row.put("my_role", rs.getString("my_role"));
                    row.put("language_pack", mapLanguagePackSummary(rs));
                    return row;
                },
                args.toArray()
        );
        return ApiResponse.success(Map.of("results", results, "total", total == null ? 0 : total));
    }

    @Override
    public ApiResponse<Object> classroomRetrieve(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Classroom not found");
        }
        Map<String, Object> detail = fetchClassroom(classroomId, user.userId());
        if (detail == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Classroom not found");
        }
        return ApiResponse.success(detail);
    }

    @Override
    public ApiResponse<Object> classroomCreate(Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!user.admin()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        String name = trimToNull(stringValue(request.get("name")));
        if (name == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "name is required");
        }
        Long languagePackId = parseLongObj(request.get("language_pack_id"));
        if (languagePackId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        Map<String, Object> languagePack = requireCompletePublishedLanguagePack(languagePackId);

        String classroomId = randomId();
        jdbcTemplate.update(
                """
                insert into classroom(id, name, description, course_code, semester, created_by_id,
                                      enable_ai_tutor, enable_collaboration, member_count, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now())
                """,
                classroomId, name,
                trimToNull(stringValue(request.get("description"))),
                trimToNull(stringValue(request.get("course_code"))),
                trimToNull(stringValue(request.get("semester"))),
                user.userId(),
                parseBoolean(request.get("enable_ai_tutor"), true),
                parseBoolean(request.get("enable_collaboration"), true)
        );
        jdbcTemplate.update(
                """
                insert into classroom_member(id, classroom_id, user_id, role, join_method, join_time, update_time)
                values (?, ?, ?, 'owner', 'created', now(), now())
                """,
                randomId(), classroomId, user.userId());
        jdbcTemplate.update(
                "insert into classroom_language_pack(classroom_id, language_pack_id, create_time) values (?, ?, now())",
                classroomId, languagePackId);
        importLanguagePackLessons(classroomId, languagePackId, user.userId());
        importLanguagePackProblems(classroomId, languagePackId);
        refreshClassroomContentCounts(classroomId);

        Map<String, Object> detail = fetchClassroom(classroomId, user.userId());
        detail.put("language_pack", languagePack);
        return ApiResponse.success(detail);
    }

    @Override
    public ApiResponse<Object> classroomUpdate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isOwner(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        int updated = jdbcTemplate.update(
                """
                update classroom
                set name = coalesce(?, name), description = coalesce(?, description),
                    course_code = coalesce(?, course_code), semester = coalesce(?, semester),
                    enable_ai_tutor = coalesce(?, enable_ai_tutor),
                    enable_collaboration = coalesce(?, enable_collaboration), update_time = now()
                where id = ?
                """,
                trimToNull(stringValue(request.get("name"))),
                trimToNull(stringValue(request.get("description"))),
                trimToNull(stringValue(request.get("course_code"))),
                trimToNull(stringValue(request.get("semester"))),
                parseBooleanObj(request.get("enable_ai_tutor")),
                parseBooleanObj(request.get("enable_collaboration")),
                classroomId
        );
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Classroom not found");
        }
        return ApiResponse.success(fetchClassroom(classroomId, user.userId()));
    }

    @Override
    public ApiResponse<Object> classroomDelete(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isOwner(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        jdbcTemplate.update("delete from classroom where id = ?", classroomId);
        return ApiResponse.success("success");
    }

    @Override
    public ApiResponse<Object> invitationJoin(Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        String code = trimToNull(stringValue(request.get("code")));
        if (code == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "code is required");
        }

        Map<String, Object> invitation = findInvitationByCode(code);
        if (invitation == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "邀请码无效或已过期");
        }
        String classroomId = stringValue(invitation.get("classroom_id"));
        if (access.isMember(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("message", "已在班级中", "classroom", fetchClassroom(classroomId, user.userId())));
        }

        String role = trimToNull(stringValue(invitation.get("default_role")));
        if (role == null) role = "student";
        jdbcTemplate.update(
                """
                insert into classroom_member(id, classroom_id, user_id, role, join_method, join_time, update_time)
                values (?, ?, ?, ?, 'invited', now(), now())
                """,
                randomId(), classroomId, user.userId(), role);
        jdbcTemplate.update("update classroom set member_count = member_count + 1, update_time = now() where id = ?", classroomId);
        jdbcTemplate.update(
                """
                update classroom_invitation
                set current_uses = current_uses + 1, last_used_time = now(),
                    is_active = case when max_uses > 0 and current_uses + 1 >= max_uses then false else is_active end
                where id = ?
                """,
                stringValue(invitation.get("id")));

        return ApiResponse.success(Map.of(
                "message", "成功加入班级 " + trimToEmpty(stringValue(invitation.get("classroom_name"))),
                "classroom", fetchClassroom(classroomId, user.userId())));
    }

    @Override
    public ApiResponse<Object> invitationGenerate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可生成邀请码");
        }
        int maxUses = parseIntObj(request.get("max_uses"), 100);
        int expireHours = parseIntObj(request.get("expire_hours"), 168);
        String defaultRole = trimToNull(stringValue(request.get("default_role")));
        if (defaultRole == null) defaultRole = "student";

        String code = access.randomCode(8);
        String id = randomId();
        jdbcTemplate.update(
                """
                insert into classroom_invitation(id, classroom_id, code_hash, code_plain, created_by_id,
                                               max_uses, current_uses, expire_time, default_role, is_active, create_time)
                values (?, ?, ?, ?, ?, ?, 0, now() + (? || ' hours')::interval, ?, true, now())
                """,
                id, classroomId, access.sha256(code), code, user.userId(),
                maxUses, String.valueOf(expireHours), defaultRole);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("invitation_id", id);
        payload.put("expires_at", nowPlusHours(expireHours));
        payload.put("usage_limit", maxUses);
        payload.put("usage_count", 0);
        return ApiResponse.success(payload);
    }

    @Override
    public ApiResponse<Object> invitationList(String classroomId, Map<String, String> params, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可查看邀请码");
        }
        boolean includeExpired = "true".equalsIgnoreCase(trimToEmpty(params.get("include_expired")));
        String where = includeExpired ? "" : " and (expire_time is null or expire_time > now()) and is_active = true";
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select id, code_plain, expire_time, max_uses, current_uses, is_active, create_time
                from classroom_invitation
                where classroom_id = ?
                """ + where + " order by create_time desc",
                (rs, rowNum) -> Map.of(
                        "invitation_id", rs.getString("id"),
                        "code", rs.getString("code_plain"),
                        "expires_at", formatTime(rs.getTimestamp("expire_time")),
                        "usage_limit", rs.getInt("max_uses"),
                        "usage_count", rs.getInt("current_uses"),
                        "is_active", rs.getBoolean("is_active"),
                        "is_valid", rs.getBoolean("is_active") && (rs.getTimestamp("expire_time") == null || rs.getTimestamp("expire_time").toInstant().isAfter(Instant.now())),
                        "create_time", formatTime(rs.getTimestamp("create_time"))
                ),
                classroomId);
        return ApiResponse.success(rows);
    }

    @Override
    public ApiResponse<Object> invitationDeactivate(String invitationId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        String classroomId = jdbcTemplate.query(
                "select classroom_id from classroom_invitation where id = ?",
                (rs, rowNum) -> rs.getString("classroom_id"),
                invitationId
        ).stream().findFirst().orElse(null);
        if (classroomId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "邀请码不存在");
        }
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        jdbcTemplate.update("update classroom_invitation set is_active = false where id = ?", invitationId);
        return ApiResponse.success(Map.of("message", "邀请码已停用"));
    }

    @Override
    public ApiResponse<Object> invitationRefresh(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可刷新邀请码");
        }
        int deactivated = jdbcTemplate.update("update classroom_invitation set is_active = false where classroom_id = ? and is_active = true", classroomId);
        ApiResponse<Object> generated = invitationGenerate(classroomId, request, authentication);
        if (generated.error() != null) return generated;
        Map<String, Object> data = castMap(generated.data());
        data.put("message", "邀请码已刷新，旧邀请码已全部失效");
        data.put("deactivated_count", deactivated);
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Object> problemList(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            return ApiResponse.success(Map.of("results", List.of()));
        }
        boolean staff = access.isStaff(classroomId, user.userId());
        String visibility = staff ? "" : " and cp.is_visible = true";

        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                select cp.id, cp.classroom_id, cp.problem_id, cp.display_order,
                       cp.is_visible, cp.is_private, cp.category, cp.difficulty_override,
                       cp.submission_count, cp.ac_count, cp.added_time,
                       p._id as problem_display_id, p.title, p.difficulty,
                       p.submission_number, p.accepted_number
                from classroom_problem cp
                join problem p on p.id = cp.problem_id
                where cp.classroom_id = ?
                """ + visibility + " order by cp.display_order asc, cp.added_time asc",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("classroom", rs.getString("classroom_id"));
                    row.put("problem_id", rs.getLong("problem_id"));
                    row.put("problem_display_id", rs.getString("problem_display_id"));
                    row.put("_id", rs.getString("problem_display_id"));
                    row.put("title", rs.getString("title"));
                    row.put("difficulty", rs.getString("difficulty"));
                    row.put("submission_number", rs.getLong("submission_number"));
                    row.put("accepted_number", rs.getLong("accepted_number"));
                    row.put("ac_rate", acRate(rs.getLong("submission_number"), rs.getLong("accepted_number")));
                    row.put("display_order", rs.getInt("display_order"));
                    row.put("is_visible", rs.getBoolean("is_visible"));
                    row.put("is_private", rs.getBoolean("is_private"));
                    row.put("category", rs.getString("category"));
                    row.put("difficulty_override", rs.getString("difficulty_override"));
                    row.put("submission_count", rs.getInt("submission_count"));
                    row.put("ac_count", rs.getInt("ac_count"));
                    row.put("added_time", formatTime(rs.getTimestamp("added_time")));
                    return row;
                },
                classroomId
        );

        Map<Long, String> myStatus = loadUserProblemStatuses(user.userId(), rows);
        for (Map<String, Object> row : rows) {
            Long pid = parseLongObj(row.get("problem_id"));
            row.put("my_status", pid == null ? null : myStatus.get(pid));
        }
        return ApiResponse.success(Map.of("results", rows));
    }

    @Override
    public ApiResponse<Object> problemRetrieve(String classroomId, String classroomProblemId, Authentication authentication) {
        ApiResponse<Object> list = problemList(classroomId, authentication);
        if (list.error() != null) return list;
        for (Object item : castList(castMap(list.data()).get("results"))) {
            Map<String, Object> row = castMap(item);
            if (classroomProblemId.equals(stringValue(row.get("id")))) {
                return ApiResponse.success(row);
            }
        }
        throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
    }

    @Override
    public ApiResponse<Object> problemCreate(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        Long problemId = parseLongObj(request.get("problem_id"));
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }
        Integer exists = jdbcTemplate.queryForObject("select count(*) from problem where id = ?", Integer.class, problemId);
        if (exists == null || exists == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        String existingId = jdbcTemplate.query(
                "select id from classroom_problem where classroom_id = ? and problem_id = ?",
                (rs, rowNum) -> rs.getString("id"),
                classroomId, problemId
        ).stream().findFirst().orElse(null);

        if (existingId == null) {
            String id = randomId();
            jdbcTemplate.update(
                    """
                    insert into classroom_problem(id, classroom_id, problem_id, display_order, is_visible, is_private,
                                                  category, difficulty_override, submission_count, ac_count, added_time, update_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, now(), now())
                    """,
                    id, classroomId, problemId,
                    parseIntObj(request.get("display_order"), 0),
                    parseBoolean(request.get("is_visible"), false),
                    parseBoolean(request.get("is_private"), true),
                    trimToNull(stringValue(request.get("category"))),
                    trimToNull(stringValue(request.get("difficulty_override"))));
            jdbcTemplate.update("update classroom set problem_count = problem_count + 1, update_time = now() where id = ?", classroomId);
            existingId = id;
        }
        return problemRetrieve(classroomId, existingId, authentication);
    }

    @Override
    public ApiResponse<Object> problemUpdate(String classroomId, String classroomProblemId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        int updated = jdbcTemplate.update(
                """
                update classroom_problem
                set is_visible = coalesce(?, is_visible), is_private = coalesce(?, is_private),
                    display_order = coalesce(?, display_order), category = coalesce(?, category),
                    difficulty_override = coalesce(?, difficulty_override), update_time = now()
                where classroom_id = ? and id = ?
                """,
                parseBooleanObj(request.get("is_visible")),
                parseBooleanObj(request.get("is_private")),
                parseIntObjNullable(request.get("display_order")),
                trimToNull(stringValue(request.get("category"))),
                trimToNull(stringValue(request.get("difficulty_override"))),
                classroomId, classroomProblemId);
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        return problemRetrieve(classroomId, classroomProblemId, authentication);
    }

    @Override
    public ApiResponse<Object> problemDelete(String classroomId, String classroomProblemId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        int deleted = jdbcTemplate.update("delete from classroom_problem where classroom_id = ? and id = ?", classroomId, classroomProblemId);
        if (deleted == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem not found");
        }
        jdbcTemplate.update("update classroom set problem_count = greatest(problem_count - 1, 0), update_time = now() where id = ?", classroomId);
        return ApiResponse.success("success");
    }

    @Override
    public ApiResponse<Object> problemImportObjectiveJson(String classroomId, Map<String, Object> request, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        List<Object> questions = castList(request.get("questions"));
        if (questions.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "questions is required");
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        for (Object obj : questions) {
            Map<String, Object> q = castMap(obj);
            String questionType = trimToNull(stringValue(q.get("question_type")));
            if (questionType == null) continue;
            String title = trimToEmpty(stringValue(q.get("title")));
            String description = trimToEmpty(stringValue(q.get("description")));
            String displayId = "OBJ-" + randomId().substring(0, 8).toUpperCase(Locale.ROOT);

            Long problemId = jdbcTemplate.queryForObject(
                    """
                    insert into problem(_id, title, description, is_public, visible, difficulty, source,
                                        statistic_info, create_time, last_update_time, created_by_id,
                                        submission_number, accepted_number)
                    values (?, ?, ?, false, true, 'Mid', 'Classroom Objective JSON Import', cast(? as jsonb), now(), now(), ?, 0, 0)
                    returning id
                    """,
                    Long.class, displayId, title, description,
                    toJson(objectMapper, Map.of("objective_question", q)), user.userId());
            if (problemId == null) continue;

            String classroomProblemId = randomId();
            jdbcTemplate.update(
                    """
                    insert into classroom_problem(id, classroom_id, problem_id, display_order, is_visible, is_private,
                                                  category, submission_count, ac_count, added_time, update_time)
                    values (?, ?, ?, 0, false, true, 'objective_import', 0, 0, now(), now())
                    """,
                    classroomProblemId, classroomId, problemId);
            imported.add(Map.of(
                    "classroom_problem_id", classroomProblemId,
                    "problem_id", problemId,
                    "_id", displayId,
                    "title", title,
                    "question_type", questionType));
        }
        jdbcTemplate.update("update classroom set problem_count = (select count(*) from classroom_problem where classroom_id = ?), update_time = now() where id = ?", classroomId, classroomId);
        return ApiResponse.success(Map.of("imported_count", imported.size(), "results", imported));
    }

    @Override
    public ApiResponse<Object> problemExportObjectiveJson(String classroomId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
        List<Object> questions = new ArrayList<>();
        List<String> jsons = jdbcTemplate.query(
                """
                select p.statistic_info::text as statistic_info_json
                from classroom_problem cp
                join problem p on p.id = cp.problem_id
                where cp.classroom_id = ?
                """,
                (rs, rowNum) -> rs.getString("statistic_info_json"),
                classroomId);
        for (String raw : jsons) {
            Map<String, Object> stat = parseJsonMap(objectMapper, raw);
            Map<String, Object> oq = castMap(stat.get("objective_question"));
            String qt = trimToNull(stringValue(oq.get("question_type")));
            if ("choice".equals(qt) || "fill_blank".equals(qt)) {
                questions.add(oq);
            }
        }
        return ApiResponse.success(Map.of(
                "version", "1.0", "source", "classroom_problem_export",
                "classroom_id", classroomId, "exported_count", questions.size(), "questions", questions));
    }

    private Map<String, Object> fetchClassroom(String classroomId, Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select c.id, c.name, c.description, c.course_code, c.semester,
                           c.is_active, c.allow_student_view_others, c.enable_ai_tutor, c.enable_collaboration,
                           coalesce(member_stats.member_count, 0) as member_count,
                           c.problem_count, c.lesson_count, c.current_chapter, c.chapter_unlock_threshold,
                           c.create_time, c.update_time, cm.role as my_role,
                           u.id as created_by_id, u.username as created_by_username,
                           lp.id as language_pack_id, lp.name as language_pack_name,
                           lp.version as language_pack_version, lp.primary_language as language_pack_primary_language
                    from classroom c
                    join classroom_member cm on cm.classroom_id = c.id and cm.user_id = ?
                    join "user" u on u.id = c.created_by_id
                    left join classroom_language_pack clp on clp.classroom_id = c.id
                    left join language_pack lp on lp.id = clp.language_pack_id
                    left join (
                        select classroom_id, count(*) as member_count
                        from classroom_member
                        group by classroom_id
                    ) member_stats on member_stats.classroom_id = c.id
                    where c.id = ?
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> createdBy = new LinkedHashMap<>();
                        createdBy.put("id", rs.getLong("created_by_id"));
                        createdBy.put("username", rs.getString("created_by_username"));
                        createdBy.put("avatar", null);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("name", rs.getString("name"));
                        row.put("description", rs.getString("description"));
                        row.put("course_code", rs.getString("course_code"));
                        row.put("semester", rs.getString("semester"));
                        row.put("created_by", createdBy);
                        row.put("is_active", rs.getBoolean("is_active"));
                        row.put("allow_student_view_others", rs.getBoolean("allow_student_view_others"));
                        row.put("enable_ai_tutor", rs.getBoolean("enable_ai_tutor"));
                        row.put("enable_collaboration", rs.getBoolean("enable_collaboration"));
                        row.put("member_count", rs.getInt("member_count"));
                        row.put("problem_count", rs.getInt("problem_count"));
                        row.put("lesson_count", rs.getInt("lesson_count"));
                        row.put("current_chapter", rs.getInt("current_chapter"));
                        row.put("chapter_unlock_threshold", rs.getDouble("chapter_unlock_threshold"));
                        row.put("create_time", formatTime(rs.getTimestamp("create_time")));
                        row.put("update_time", formatTime(rs.getTimestamp("update_time")));
                        row.put("my_role", rs.getString("my_role"));
                        row.put("language_pack", mapLanguagePackSummary(rs));
                        return row;
                    },
                    userId, classroomId);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> findInvitationByCode(String code) {
        String hash = access.sha256(code.toUpperCase(Locale.ROOT));
        return jdbcTemplate.query(
                """
                select i.id, i.classroom_id, i.default_role, c.name as classroom_name,
                       i.max_uses, i.current_uses, i.expire_time, i.is_active
                from classroom_invitation i
                join classroom c on c.id = i.classroom_id
                where i.code_hash = ? and i.is_active = true
                """,
                (rs, rowNum) -> {
                    Timestamp expireTime = rs.getTimestamp("expire_time");
                    if (expireTime != null && expireTime.toInstant().isBefore(Instant.now())) return null;
                    int maxUses = rs.getInt("max_uses");
                    int currentUses = rs.getInt("current_uses");
                    if (maxUses > 0 && currentUses >= maxUses) return null;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getString("id"));
                    item.put("classroom_id", rs.getString("classroom_id"));
                    item.put("default_role", rs.getString("default_role"));
                    item.put("classroom_name", rs.getString("classroom_name"));
                    return item;
                },
                hash
        ).stream().filter(v -> v != null).findFirst().orElse(null);
    }

    private Map<String, Object> requireCompletePublishedLanguagePack(Long languagePackId) {
        Map<String, Object> summary = jdbcTemplate.query(
                "select lp.id, lp.name, lp.version, lp.primary_language from language_pack lp where lp.id = ? and lp.status = 'published'",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("name", rs.getString("name"));
                    item.put("version", rs.getInt("version"));
                    item.put("primary_language", rs.getString("primary_language"));
                    return item;
                },
                languagePackId
        ).stream().findFirst().orElse(null);
        if (summary == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language pack not found");
        }
        Integer documentCount = jdbcTemplate.queryForObject("select count(*) from language_pack_document where language_pack_id = ? and status = 'normalized' and canonical_path is not null", Integer.class, languagePackId);
        Integer kcCount = jdbcTemplate.queryForObject("select count(*) from language_pack_kc where language_pack_id = ?", Integer.class, languagePackId);
        Integer problemCount = jdbcTemplate.queryForObject("select count(*) from language_pack_problem_mapping where language_pack_id = ?", Integer.class, languagePackId);
        if ((documentCount == null || documentCount <= 0) || (kcCount == null || kcCount <= 0) || (problemCount == null || problemCount <= 0)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language pack is incomplete");
        }
        return summary;
    }

    private Map<Long, String> loadUserProblemStatuses(Long userId, List<Map<String, Object>> rows) {
        List<Long> problemIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long pid = parseLongObj(row.get("problem_id"));
            if (pid != null) problemIds.add(pid);
        }
        if (problemIds.isEmpty()) return Map.of();
        String in = String.join(",", problemIds.stream().map(String::valueOf).toList());
        List<Map<String, Object>> submissions = jdbcTemplate.query(
                "select problem_id, result from submission where user_id = ? and problem_id in (" + in + ")",
                (rs, rowNum) -> Map.of("problem_id", rs.getLong("problem_id"), "result", rs.getInt("result")),
                userId);
        Map<Long, String> statuses = new LinkedHashMap<>();
        for (Map<String, Object> sub : submissions) {
            Long pid = parseLongObj(sub.get("problem_id"));
            Integer result = parseIntObjNullable(sub.get("result"));
            if (pid == null || result == null) continue;
            if ("AC".equals(statuses.get(pid))) continue;
            statuses.put(pid, result == 0 ? "AC" : "WA");
        }
        return statuses;
    }

    private Map<String, Object> mapLanguagePackSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object rawId = rs.getObject("language_pack_id");
        if (rawId == null) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ((Number) rawId).longValue());
        item.put("name", rs.getString("language_pack_name"));
        item.put("version", rs.getInt("language_pack_version"));
        item.put("primary_language", rs.getString("language_pack_primary_language"));
        return item;
    }

    private void importLanguagePackLessons(String classroomId, Long languagePackId, Long createdById) {
        List<Map<String, Object>> documents = jdbcTemplate.query(
                """
                select original_filename, canonical_path, file_size_bytes, page_count
                from language_pack_document
                where language_pack_id = ? and status = 'normalized' and canonical_path is not null
                order by sort_order asc, id asc
                """,
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("original_filename", rs.getString("original_filename"));
                    item.put("canonical_path", rs.getString("canonical_path"));
                    item.put("file_size_bytes", rs.getLong("file_size_bytes"));
                    item.put("page_count", rs.getInt("page_count"));
                    return item;
                },
                languagePackId);
        Path classroomFolder = lessonRoot.resolve(classroomId);
        try {
            Files.createDirectories(classroomFolder);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create classroom lesson directory", exception);
        }
        List<Object[]> batchParams = new ArrayList<>(documents.size());
        for (int index = 0; index < documents.size(); index++) {
            Map<String, Object> document = documents.get(index);
            String originalFilename = trimToEmpty(stringValue(document.get("original_filename")));
            String canonicalPath = trimToNull(stringValue(document.get("canonical_path")));
            String lessonType = lessonTypeFromFilename(originalFilename, canonicalPath);
            if (canonicalPath == null || lessonType == null) {
                throw new IllegalStateException("unsupported language pack lesson file");
            }
            Path sourcePath = Path.of(canonicalPath).normalize();
            if (!Files.isRegularFile(sourcePath)) {
                throw new IllegalStateException("language pack lesson file does not exist");
            }
            String safeName = randomId() + extension(sourcePath.getFileName().toString());
            Path targetPath = classroomFolder.resolve(safeName);
            try {
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new IllegalStateException("failed to import language pack lesson", exception);
            }
            batchParams.add(new Object[]{
                    randomId(), classroomId, originalFilename, null, lessonType,
                    classroomId + "/" + safeName,
                    parseLongObj(document.get("file_size_bytes")) == null ? 0L : parseLongObj(document.get("file_size_bytes")),
                    parseIntObj(document.get("page_count"), 0),
                    "[]", index, createdById
            });
        }
        if (!batchParams.isEmpty()) {
            String sql = """
                    insert into classroom_lesson(id, classroom_id, title, description, lesson_type,
                                                 file_path, file_size, total_pages, table_of_contents,
                                                 display_order, created_by_id, create_time, update_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, now(), now())
                    """;
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    Object[] p = batchParams.get(i);
                    ps.setString(1, (String) p[0]);
                    ps.setString(2, (String) p[1]);
                    ps.setString(3, (String) p[2]);
                    ps.setNull(4, java.sql.Types.VARCHAR);
                    ps.setString(5, (String) p[4]);
                    ps.setString(6, (String) p[5]);
                    ps.setLong(7, (Long) p[6]);
                    ps.setInt(8, (Integer) p[7]);
                    ps.setString(9, (String) p[8]);
                    ps.setInt(10, (Integer) p[9]);
                    ps.setLong(11, (Long) p[10]);
                }

                @Override
                public int getBatchSize() {
                    return batchParams.size();
                }
            });
        }
    }

    private void importLanguagePackProblems(String classroomId, Long languagePackId) {
        List<Long> problemIds = jdbcTemplate.query(
                """
                select lpm.problem_id
                from language_pack_problem_mapping lpm
                join problem p on p.id = lpm.problem_id
                where lpm.language_pack_id = ?
                order by lpm.create_time asc, lpm.id asc
                """,
                (rs, rowNum) -> rs.getLong("problem_id"),
                languagePackId);
        if (!problemIds.isEmpty()) {
            List<Object[]> batchParams = new ArrayList<>(problemIds.size());
            for (int index = 0; index < problemIds.size(); index++) {
                batchParams.add(new Object[]{ randomId(), classroomId, problemIds.get(index), index });
            }
            String sql = """
                    insert into classroom_problem(id, classroom_id, problem_id, display_order, is_visible, is_private,
                                                  category, difficulty_override, submission_count, ac_count, added_time, update_time)
                    values (?, ?, ?, ?, true, false, null, null, 0, 0, now(), now())
                    """;
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    Object[] p = batchParams.get(i);
                    ps.setString(1, (String) p[0]);
                    ps.setString(2, (String) p[1]);
                    ps.setLong(3, (Long) p[2]);
                    ps.setInt(4, (Integer) p[3]);
                }

                @Override
                public int getBatchSize() {
                    return batchParams.size();
                }
            });
        }
    }

    private void refreshClassroomContentCounts(String classroomId) {
        jdbcTemplate.update(
                """
                update classroom
                set lesson_count = (select count(*) from classroom_lesson where classroom_id = ?),
                    problem_count = (select count(*) from classroom_problem where classroom_id = ?),
                    update_time = now()
                where id = ?
                """,
                classroomId, classroomId, classroomId);
    }

    private String lessonTypeFromFilename(String originalFilename, String canonicalPath) {
        String ext = extension(trimToNull(originalFilename) == null ? canonicalPath : originalFilename).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case ".pdf" -> "pdf";
            case ".ppt", ".pptx" -> "ppt";
            case ".doc", ".docx" -> "doc";
            case ".md", ".markdown" -> "markdown";
            default -> null;
        };
    }
}

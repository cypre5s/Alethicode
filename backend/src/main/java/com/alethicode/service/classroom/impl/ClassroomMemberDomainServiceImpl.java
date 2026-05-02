package com.alethicode.service.classroom.impl;

import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.classroom.ClassroomAccessHelper;
import com.alethicode.service.classroom.ClassroomAccessHelper.UserAuth;
import com.alethicode.service.classroom.ClassroomMemberDomainService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.alethicode.util.ServiceParseUtils.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClassroomMemberDomainServiceImpl implements ClassroomMemberDomainService {

    private final JdbcTemplate jdbcTemplate;
    private final ClassroomAccessHelper access;

    public ClassroomMemberDomainServiceImpl(JdbcTemplate jdbcTemplate,
                                            ClassroomAccessHelper access) {
        this.jdbcTemplate = jdbcTemplate;
        this.access = access;
    }

    @Override
    public ApiResponse<Object> memberList(String classroomId, Map<String, String> params, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅班级成员可查看成员列表");
        }
        String role = trimToNull(params.get("role"));
        String where = role == null ? "" : " and cm.role = ?";
        List<Map<String, Object>> rows;
        if (role == null) {
            rows = jdbcTemplate.query(
                    """
                    select cm.id, cm.role, cm.join_method, cm.nickname, cm.student_id,
                           cm.problems_solved, cm.last_active_time, cm.join_time,
                           u.id as user_id, u.username
                    from classroom_member cm
                    join "user" u on u.id = cm.user_id
                    where cm.classroom_id = ?
                    order by cm.join_time asc
                    """,
                    (rs, rowNum) -> mapMember(rs),
                    classroomId
            );
        } else {
            rows = jdbcTemplate.query(
                    """
                    select cm.id, cm.role, cm.join_method, cm.nickname, cm.student_id,
                           cm.problems_solved, cm.last_active_time, cm.join_time,
                           u.id as user_id, u.username
                    from classroom_member cm
                    join "user" u on u.id = cm.user_id
                    where cm.classroom_id = ?
                    """ + where + " order by cm.join_time asc",
                    (rs, rowNum) -> mapMember(rs),
                    classroomId,
                    role
            );
        }
        return ApiResponse.success(Map.of("results", rows, "total", rows.size()));
    }

    @Override
    public ApiResponse<Object> memberRetrieve(String classroomId, String memberId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isMember(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅班级成员可查看成员详情");
        }
        Map<String, Object> detail = jdbcTemplate.query(
                """
                select cm.id, cm.role, cm.join_method, cm.nickname, cm.student_id,
                       cm.problems_solved, cm.last_active_time, cm.join_time,
                       u.id as user_id, u.username
                from classroom_member cm
                join "user" u on u.id = cm.user_id
                where cm.classroom_id = ? and cm.id = ?
                """,
                (rs, rowNum) -> mapMember(rs),
                classroomId,
                memberId
        ).stream().findFirst().orElse(null);
        if (detail == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Member not found");
        }
        return ApiResponse.success(detail);
    }

    @Override
    public ApiResponse<Object> memberPromote(String classroomId, String memberId, Authentication authentication) {
        return memberRoleChange(classroomId, memberId, authentication, "student", "ta", "仅学生可被提拔为助教");
    }

    @Override
    public ApiResponse<Object> memberDemote(String classroomId, String memberId, Authentication authentication) {
        return memberRoleChange(classroomId, memberId, authentication, "ta", "student", "仅助教可被降级为学生");
    }

    @Override
    public ApiResponse<Object> memberDelete(String classroomId, String memberId, Authentication authentication) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isStaff(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅教师/助教可移除成员");
        }
        String role = access.memberRole(classroomId, memberId);
        if (role == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "成员不存在");
        }
        if ("owner".equals(role)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "不能移除班级创建者");
        }
        jdbcTemplate.update("delete from classroom_member where classroom_id = ? and id = ?", classroomId, memberId);
        jdbcTemplate.update("update classroom set member_count = greatest(member_count - 1, 0), update_time = now() where id = ?", classroomId);
        return ApiResponse.success(Map.of("message", "成员已移除"));
    }

    private ApiResponse<Object> memberRoleChange(String classroomId, String memberId, Authentication authentication,
                                                  String fromRole, String toRole, String errorMsg) {
        UserAuth user = access.requireAuthenticated(authentication);
        if (!access.isOwner(classroomId, user.userId())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "仅班级创建者可管理助教");
        }
        int updated = jdbcTemplate.update(
                "update classroom_member set role = ?, update_time = now() where classroom_id = ? and id = ? and role = ?",
                toRole, classroomId, memberId, fromRole);
        if (updated == 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", errorMsg);
        }
        String username = jdbcTemplate.query(
                "select u.username from classroom_member cm join \"user\" u on u.id = cm.user_id where cm.id = ?",
                (rs, rowNum) -> rs.getString("username"),
                memberId
        ).stream().findFirst().orElse("");
        return ApiResponse.success(Map.of("message", username + ("ta".equals(toRole) ? " 已提拔为助教" : " 已降级为学生")));
    }

    private Map<String, Object> mapMember(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", rs.getLong("user_id"));
        user.put("username", rs.getString("username"));
        user.put("avatar", null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("user", user);
        row.put("role", rs.getString("role"));
        row.put("role_display", rs.getString("role"));
        row.put("join_method", rs.getString("join_method"));
        row.put("join_method_display", rs.getString("join_method"));
        row.put("nickname", rs.getString("nickname"));
        row.put("student_id", rs.getString("student_id"));
        row.put("problems_solved", rs.getInt("problems_solved"));
        row.put("last_active_time", formatTime(rs.getTimestamp("last_active_time")));
        row.put("join_time", formatTime(rs.getTimestamp("join_time")));
        return row;
    }
}

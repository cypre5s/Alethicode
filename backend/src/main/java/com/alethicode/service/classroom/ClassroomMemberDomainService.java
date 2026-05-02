package com.alethicode.service.classroom;

import com.alethicode.dto.response.ApiResponse;
import org.springframework.security.core.Authentication;

import java.util.Map;

public interface ClassroomMemberDomainService {

    ApiResponse<Object> memberList(String classroomId, Map<String, String> params, Authentication authentication);

    ApiResponse<Object> memberRetrieve(String classroomId, String memberId, Authentication authentication);

    ApiResponse<Object> memberPromote(String classroomId, String memberId, Authentication authentication);

    ApiResponse<Object> memberDemote(String classroomId, String memberId, Authentication authentication);

    ApiResponse<Object> memberDelete(String classroomId, String memberId, Authentication authentication);
}

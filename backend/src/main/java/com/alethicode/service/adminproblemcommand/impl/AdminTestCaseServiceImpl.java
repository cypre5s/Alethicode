package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminTestCaseService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.springframework.jdbc.core.JdbcTemplate;

@Service
@Transactional(rollbackFor = Exception.class)
public class AdminTestCaseServiceImpl implements AdminTestCaseService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;

    public AdminTestCaseServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ApiResponse<Object> uploadTestCases(String spjParam, MultipartFile file, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        if (spjParam == null || spjParam.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        }
        if (file == null || file.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        }

        boolean spj = "true".equalsIgnoreCase(spjParam);
        Path tempZip;
        try {
            tempZip = Files.createTempFile("alethicode-testcase-", ".zip");
            file.transferTo(tempZip);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        }

        try {
            ProcessedTestCases processed = processZip(tempZip, spj);
            return ApiResponse.success(Map.of(
                    "id", processed.testCaseId(),
                    "info", processed.info(),
                    "spj", spj
            ));
        } catch (BadZipException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public ResponseEntity<Resource> downloadTestCases(String problemIdParam, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return jsonErrorAsOctet(permissionContext.errorResponse());
        }
        if (problemIdParam == null || problemIdParam.isBlank()) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Parameter error, problem_id is required"));
        }

        Long problemId;
        try {
            problemId = Long.parseLong(problemIdParam);
        } catch (NumberFormatException ignored) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Problem does not exists"));
        }

        ProblemOwnership ownership = loadProblemOwnership(problemId);
        if (!ownership.exists()) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Problem does not exists"));
        }
        if (!canManageProblem(permissionContext, problemId, authentication.getName(), ownership.createdByUsername())) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Problem does not exist"));
        }

        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), ownership.testCaseId());
        if (!Files.isDirectory(testCaseDir)) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Test case does not exists"));
        }

        List<String> names;
        try {
            try (Stream<Path> files = Files.list(testCaseDir)) {
                names = filterNameList(
                        files.map(path -> path.getFileName().toString()).toList(),
                        false,
                        ""
                );
            }
        } catch (IOException exception) {
            return jsonErrorAsOctet(ApiResponse.error("error", "Test case does not exists"));
        }
        names = new ArrayList<>(names);
        names.add("info");

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutput = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                for (String name : names) {
                    Path filePath = testCaseDir.resolve(name);
                    if (!Files.isRegularFile(filePath)) {
                        continue;
                    }
                    zipOutput.putNextEntry(new ZipEntry(name));
                    zipOutput.write(Files.readAllBytes(filePath));
                    zipOutput.closeEntry();
                }
            }

            byte[] content = buffer.toByteArray();
            ByteArrayResource resource = new ByteArrayResource(content);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=problem_" + problemId + "_test_cases.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(resource);
        } catch (IOException exception) {
            return jsonErrorAsOctet(ApiResponse.error("error", "server error"));
        }
    }

    @Override
    public ApiResponse<Object> getInlineTestCases(String problemIdParam, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        if (problemIdParam == null || problemIdParam.isBlank()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "题目 ID 不能为空");
        }

        Long problemId;
        try {
            problemId = Long.parseLong(problemIdParam);
        } catch (NumberFormatException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exists");
        }

        ProblemOwnership ownership = loadProblemOwnership(problemId);
        if (!ownership.exists()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exists");
        }
        if (!canManageProblem(permissionContext, problemId, authentication.getName(), ownership.createdByUsername())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }
        if (ownership.testCaseId() == null || ownership.testCaseId().isBlank()) {
            return ApiResponse.success(Map.of("cases", List.of()));
        }

        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), ownership.testCaseId());
        if (!Files.isDirectory(testCaseDir)) {
            return ApiResponse.success(Map.of("cases", List.of()));
        }

        List<String> names;
        try {
            try (Stream<Path> files = Files.list(testCaseDir)) {
                names = filterNameList(files.map(path -> path.getFileName().toString()).toList(), false, "");
            }
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
        }

        List<Map<String, Object>> cases = new ArrayList<>();
        for (int i = 0; i + 1 < names.size(); i += 2) {
            Path inPath = testCaseDir.resolve(names.get(i));
            Path outPath = testCaseDir.resolve(names.get(i + 1));
            try {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("input", Files.readString(inPath, StandardCharsets.UTF_8));
                item.put("output", Files.readString(outPath, StandardCharsets.UTF_8));
                cases.add(item);
            } catch (IOException exception) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
            }
        }
        return ApiResponse.success(Map.of("cases", cases));
    }

    @Override
    public ApiResponse<Object> uploadInlineTestCases(Map<String, Object> request, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        if (request == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "cases is required");
        }
        Object casesObj = request.get("cases");
        if (!(casesObj instanceof List<?> rawCases)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "cases is required");
        }
        if (rawCases.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "cases is required");
        }

        String testCaseId = randomString(32);
        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(testCaseDir);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
        }

        List<Map<String, Object>> infoList = new ArrayList<>();
        Map<String, Object> testCaseInfo = new LinkedHashMap<>();
        testCaseInfo.put("spj", false);
        Map<String, Object> testCases = new LinkedHashMap<>();
        testCaseInfo.put("test_cases", testCases);

        for (int index = 0; index < rawCases.size(); index++) {
            Object itemObj = rawCases.get(index);
            if (!(itemObj instanceof Map<?, ?> itemMap)) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "cases[" + index + "] invalid");
            }
            String inputContent = ensureTrailingNewline(normalizeLineEnding(stringValue(itemMap.get("input"))));
            String outputContent = ensureTrailingNewline(normalizeLineEnding(stringValue(itemMap.get("output"))));
            String inputName = (index + 1) + ".in";
            String outputName = (index + 1) + ".out";
            try {
                Files.writeString(testCaseDir.resolve(inputName), inputContent, StandardCharsets.UTF_8);
                Files.writeString(testCaseDir.resolve(outputName), outputContent, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("stripped_output_md5", md5Hex(rstripWhitespace(outputContent.getBytes(StandardCharsets.UTF_8))));
            info.put("input_size", inputContent.getBytes(StandardCharsets.UTF_8).length);
            info.put("output_size", outputContent.getBytes(StandardCharsets.UTF_8).length);
            info.put("input_name", inputName);
            info.put("output_name", outputName);
            infoList.add(info);
            testCases.put(String.valueOf(index + 1), info);
        }

        try {
            Files.writeString(
                    testCaseDir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testCaseInfo),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
        }

        return ApiResponse.success(Map.of("id", testCaseId, "info", infoList));
    }

    private ProcessedTestCases processZip(Path zipPath, boolean spj) throws BadZipException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            List<String> names = zipFile.stream().map(ZipEntry::getName).toList();
            List<String> testCaseFiles = filterNameList(names, spj, "");
            if (testCaseFiles.isEmpty()) {
                throw new BadZipException("Empty file");
            }

            String testCaseId = randomString(32);
            Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
            Files.createDirectories(testCaseDir);

            Map<String, Integer> sizeCache = new HashMap<>();
            Map<String, String> md5Cache = new HashMap<>();
            for (String name : testCaseFiles) {
                ZipEntry entry = zipFile.getEntry(name);
                if (entry == null) {
                    continue;
                }
                byte[] content = readEntry(zipFile, entry);
                content = normalizeLineEnding(content);
                sizeCache.put(name, content.length);
                if (name.endsWith(".out")) {
                    md5Cache.put(name, md5Hex(rstripWhitespace(content)));
                }
                Files.write(testCaseDir.resolve(name), content);
            }

            Map<String, Object> testCaseInfo = new LinkedHashMap<>();
            testCaseInfo.put("spj", spj);
            Map<String, Object> testCases = new LinkedHashMap<>();
            testCaseInfo.put("test_cases", testCases);
            List<Map<String, Object>> infoList = new ArrayList<>();

            if (spj) {
                for (int i = 0; i < testCaseFiles.size(); i++) {
                    String input = testCaseFiles.get(i);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("input_name", input);
                    item.put("input_size", sizeCache.getOrDefault(input, 0));
                    infoList.add(item);
                    testCases.put(String.valueOf(i + 1), item);
                }
            } else {
                for (int i = 0; i + 1 < testCaseFiles.size(); i += 2) {
                    String inName = testCaseFiles.get(i);
                    String outName = testCaseFiles.get(i + 1);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stripped_output_md5", md5Cache.getOrDefault(outName, ""));
                    item.put("input_size", sizeCache.getOrDefault(inName, 0));
                    item.put("output_size", sizeCache.getOrDefault(outName, 0));
                    item.put("input_name", inName);
                    item.put("output_name", outName);
                    infoList.add(item);
                    testCases.put(String.valueOf((i / 2) + 1), item);
                }
            }

            Files.writeString(
                    testCaseDir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testCaseInfo),
                    StandardCharsets.UTF_8
            );

            return new ProcessedTestCases(testCaseId, infoList);
        } catch (IOException exception) {
            throw new BadZipException("Bad zip file");
        }
    }

    private List<String> filterNameList(List<String> nameList, boolean spj, String dir) {
        Set<String> nameSet = Set.copyOf(nameList);
        List<String> result = new ArrayList<>();
        int prefix = 1;
        while (true) {
            String inName = prefix + ".in";
            if (spj) {
                if (nameSet.contains(dir + inName)) {
                    result.add(inName);
                    prefix++;
                    continue;
                }
                return result;
            }

            String outName = prefix + ".out";
            if (nameSet.contains(dir + inName) && nameSet.contains(dir + outName)) {
                result.add(inName);
                result.add(outName);
                prefix++;
                continue;
            }
            return result;
        }
    }

    private PermissionContext resolvePermission(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return new PermissionContext(false, false, Set.of(), ApiResponse.error("permission-denied", "请先登录"));
        }
        try {
            return jdbcTemplate.queryForObject(
                    "select id, admin_type, problem_permission, is_disabled from \"user\" where username = ?",
                    (rs, rowNum) -> {
                        Long userId = rs.getLong("id");
                        String adminType = rs.getString("admin_type");
                        String problemPermission = rs.getString("problem_permission");
                        boolean disabled = rs.getBoolean("is_disabled");
                        boolean teacher = "Teacher".equals(adminType);
                        boolean adminRole = "Admin".equals(adminType) || teacher;
                        boolean hasProblemPermission = teacher || (adminRole
                                && problemPermission != null
                                && !"None".equals(problemPermission));
                        if (!hasProblemPermission) {
                            return new PermissionContext(false, teacher, Set.of(), ApiResponse.error("permission-denied", "请先登录"));
                        }
                        if (disabled) {
                            return new PermissionContext(false, teacher, Set.of(), ApiResponse.error("permission-denied", "你的账号已被禁用"));
                        }
                        boolean canManageAllProblems = "Admin".equals(adminType) || teacher || "All".equals(problemPermission);
                        Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : Set.of();
                        return new PermissionContext(canManageAllProblems, teacher, accessibleLanguagePackIds, null);
                    },
                    authentication.getName()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new PermissionContext(false, false, Set.of(), ApiResponse.error("permission-denied", "请先登录"));
        }
    }

    private Set<Long> loadTeacherLanguagePackIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return Set.copyOf(jdbcTemplate.queryForList(
                """
                select distinct clp.language_pack_id
                from classroom_member cm
                join classroom c on c.id = cm.classroom_id
                join classroom_language_pack clp on clp.classroom_id = cm.classroom_id
                where cm.user_id = ?
                  and c.is_active = true
                  and cm.role in ('owner', 'ta')
                """,
                Long.class,
                userId
        ));
    }

    private boolean canManageProblem(PermissionContext permissionContext, Long problemId, String actor, String owner) {
        if (permissionContext.canManageAllProblems()) {
            return true;
        }
        if (permissionContext.teacher()) {
            return problemInLanguagePackScope(problemId, permissionContext.accessibleLanguagePackIds());
        }
        return actor != null && actor.equals(owner);
    }

    private boolean problemInLanguagePackScope(Long problemId, Set<Long> accessibleLanguagePackIds) {
        if (problemId == null || accessibleLanguagePackIds == null || accessibleLanguagePackIds.isEmpty()) {
            return false;
        }
        String placeholders = accessibleLanguagePackIds.stream().map(v -> "?").collect(java.util.stream.Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(problemId);
        args.addAll(accessibleLanguagePackIds);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from language_pack_problem_mapping where problem_id = ? and language_pack_id in (" + placeholders + ")",
                Integer.class,
                args.toArray()
        );
        return count != null && count > 0;
    }

    private ProblemOwnership loadProblemOwnership(Long problemId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select p.id, p.test_case_id, u.username as created_by_username
                    from problem p
                    left join "user" u on u.id = p.created_by_id
                    where p.id = ?
                    """,
                    (rs, rowNum) -> new ProblemOwnership(
                            true,
                            rs.getString("test_case_id"),
                            rs.getString("created_by_username")
                    ),
                    problemId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new ProblemOwnership(false, null, null);
        }
    }

    private byte[] readEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream input = zipFile.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private byte[] normalizeLineEnding(byte[] input) {
        byte[] pattern = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < input.length; i++) {
            if (i + 1 < input.length && input[i] == pattern[0] && input[i + 1] == pattern[1]) {
                output.writeBytes(replacement);
                i++;
            } else {
                output.write(input[i]);
            }
        }
        return output.toByteArray();
    }

    private String normalizeLineEnding(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String ensureTrailingNewline(String value) {
        if (value == null || value.isEmpty()) {
            return "\n";
        }
        return value.endsWith("\n") ? value : value + "\n";
    }

    private byte[] rstripWhitespace(byte[] input) {
        int end = input.length;
        while (end > 0) {
            byte b = input[end - 1];
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t' || b == '\f' || b == 0x0B) {
                end--;
                continue;
            }
            break;
        }
        byte[] trimmed = new byte[end];
        System.arraycopy(input, 0, trimmed, 0, end);
        return trimmed;
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm not available", exception);
        }
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ID_CHARS[RANDOM.nextInt(ID_CHARS.length)]);
        }
        return builder.toString();
    }

    private ResponseEntity<Resource> jsonErrorAsOctet(ApiResponse<Object> response) {
        try {
            byte[] content = objectMapper.writeValueAsBytes(response);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ByteArrayResource(content));
        } catch (JsonProcessingException exception) {
            byte[] fallback = "{\"error\":\"error\",\"data\":\"server error\"}".getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ByteArrayResource(fallback));
        }
    }

    private record PermissionContext(
            boolean canManageAllProblems,
            boolean teacher,
            Set<Long> accessibleLanguagePackIds,
            ApiResponse<Object> errorResponse
    ) {
    }

    private record ProblemOwnership(
            boolean exists,
            String testCaseId,
            String createdByUsername
    ) {
    }

    private record ProcessedTestCases(
            String testCaseId,
            List<Map<String, Object>> info
    ) {
    }

    private static class BadZipException extends Exception {
        private BadZipException(String message) {
            super(message);
        }
    }
}

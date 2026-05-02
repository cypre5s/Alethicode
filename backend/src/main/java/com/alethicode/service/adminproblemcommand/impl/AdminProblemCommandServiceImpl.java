package com.alethicode.service.adminproblemcommand.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alethicode.config.AlethicodeProperties;
import com.alethicode.dto.request.AdminProblemUpsertRequest;
import com.alethicode.dto.request.ProblemSampleRequest;
import com.alethicode.dto.request.ProblemTestCaseScoreRequest;
import com.alethicode.dto.response.ApiResponse;
import com.alethicode.service.adminproblemcommand.AdminProblemCommandService;
import com.alethicode.service.adminproblemcommand.AdminProblemQueryService;
import com.alethicode.service.languagepack.LanguagePackProblemPackage;
import com.alethicode.service.languagepack.ProblemPackageWriteOptions;
import com.alethicode.service.languagepack.ProblemPackageWriteResult;
import com.alethicode.service.languagepack.ProblemPackageWriteService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Service
@Transactional(rollbackFor = Exception.class)
public class AdminProblemCommandServiceImpl implements AdminProblemCommandService {

    private static final Set<String> ALLOWED_DIFFICULTY = Set.of("Low", "Mid", "High");
    private static final Set<String> QUESTION_TYPE_TAGS = Set.of("type:coding", "type:choice", "type:fill_blank");
    private static final Pattern TEMPLATE_BLOCK_PATTERN = Pattern.compile(
            "(?s)^//PREPEND BEGIN\\n(.*?)//PREPEND END\\n\\n//TEMPLATE BEGIN\\n(.*?)//TEMPLATE END\\n\\n//APPEND BEGIN\\n(.*?)//APPEND END\\s*$"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AlethicodeProperties properties;
    private final AdminProblemQueryService adminProblemQueryService;
    private final ProblemPackageWriteService problemPackageWriteService;

    public AdminProblemCommandServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AlethicodeProperties properties,
            AdminProblemQueryService adminProblemQueryService,
            ProblemPackageWriteService problemPackageWriteService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.adminProblemQueryService = adminProblemQueryService;
        this.problemPackageWriteService = problemPackageWriteService;
    }

    @Override
    public ApiResponse<Object> createProblem(AdminProblemUpsertRequest request, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        Long languagePackId = normalizeLanguagePackId(request.languagePackId());
        ensureLanguagePackAccess(permissionContext, languagePackId);

        String displayId = trimToNull(request.displayId());
        if (displayId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID is required");
        }
        if (existsDisplayId(displayId, null)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID already exists");
        }
        String filenameCheck = checkTestCaseFilenames(request.testCaseId(), request.testCaseScore());
        if (filenameCheck != null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", filenameCheck);
        }
        if (!ALLOWED_DIFFICULTY.contains(request.difficulty())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "difficulty: Invalid difficulty");
        }

        Long createdById = findUserIdByUsername(authentication.getName());
        if (createdById == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }

        Map<String, Object> statisticInfo = normalizeStatisticInfo(request.statisticInfo());
        Long problemId = jdbcTemplate.queryForObject(
                """
                insert into problem(
                    _id, title, description, input_description, output_description,
                    samples, test_case_id, test_case_score, hint,
                    languages, template, created_by_id, time_limit, memory_limit,
                    reference_solution_language, reference_solution_code,
                    visible, difficulty, source, statistic_info, is_ai_generated, visibility_status
                ) values (
                    ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, cast(? as jsonb), ?,
                    cast(? as jsonb), cast(? as jsonb), ?, ?, ?,
                    ?, ?, ?, ?, ?, cast(? as jsonb), false, 'class_private'
                ) returning id
                """,
                Long.class,
                displayId,
                request.title(),
                request.description(),
                request.inputDescription(),
                request.outputDescription(),
                writeJson(request.samples()),
                request.testCaseId(),
                writeJson(request.testCaseScore()),
                request.hint(),
                writeJson(request.languages()),
                writeJson(request.template()),
                createdById,
                request.timeLimit(),
                request.memoryLimit(),
                request.referenceSolutionLanguage(),
                request.referenceSolutionCode(),
                request.visible(),
                request.difficulty(),
                request.source(),
                writeJson(statisticInfo)
        );
        if (problemId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "server error");
        }

        replaceProblemTags(problemId, request.tags(), true);
        upsertLanguagePackMapping(languagePackId, problemId);
        return adminProblemQueryService.getAdminProblems(Map.of("id", String.valueOf(problemId)), authentication);
    }

    @Override
    public ApiResponse<Object> updateProblem(AdminProblemUpsertRequest request, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        Long languagePackId = normalizeLanguagePackId(request.languagePackId());
        ensureLanguagePackAccess(permissionContext, languagePackId);
        if (request.id() == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }

        OwnershipContext ownership = findOwnership(request.id());
        if (!ownership.exists()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }
        if (!canManageProblem(permissionContext, request.id(), authentication.getName(), ownership.createdByUsername())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }

        String displayId = trimToNull(request.displayId());
        if (displayId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID is required");
        }
        if (existsDisplayId(displayId, request.id())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID already exists");
        }

        String filenameCheck = checkTestCaseFilenames(request.testCaseId(), request.testCaseScore());
        if (filenameCheck != null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", filenameCheck);
        }
        if (!ALLOWED_DIFFICULTY.contains(request.difficulty())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "difficulty: Invalid difficulty");
        }

        Map<String, Object> statisticInfo = normalizeStatisticInfo(request.statisticInfo());
        jdbcTemplate.update(
                """
                update problem
                set _id = ?,
                    title = ?,
                    description = ?,
                    input_description = ?,
                    output_description = ?,
                    samples = cast(? as jsonb),
                    test_case_id = ?,
                    test_case_score = cast(? as jsonb),
                    hint = ?,
                    languages = cast(? as jsonb),
                    template = cast(? as jsonb),
                    time_limit = ?,
                    memory_limit = ?,
                    reference_solution_language = ?,
                    reference_solution_code = ?,
                    visible = ?,
                    difficulty = ?,
                    source = ?,
                    statistic_info = cast(? as jsonb),
                    last_update_time = now()
                where id = ?
                """,
                displayId,
                request.title(),
                request.description(),
                request.inputDescription(),
                request.outputDescription(),
                writeJson(request.samples()),
                request.testCaseId(),
                writeJson(request.testCaseScore()),
                request.hint(),
                writeJson(request.languages()),
                writeJson(request.template()),
                request.timeLimit(),
                request.memoryLimit(),
                request.referenceSolutionLanguage(),
                request.referenceSolutionCode(),
                request.visible(),
                request.difficulty(),
                request.source(),
                writeJson(statisticInfo),
                request.id()
        );

        replaceProblemTags(request.id(), request.tags(), true);
        upsertLanguagePackMapping(languagePackId, request.id());
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Object> deleteProblem(String idParam, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }

        String normalizedId = trimToNull(idParam);
        if (normalizedId == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid parameter, id is required");
        }

        Long problemId;
        try {
            problemId = Long.parseLong(normalizedId);
        } catch (NumberFormatException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exists");
        }

        OwnershipContext ownership = findOwnership(problemId);
        if (!ownership.exists()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exists");
        }
        if (!canManageProblem(permissionContext, problemId, authentication.getName(), ownership.createdByUsername())) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Problem does not exist");
        }

        jdbcTemplate.update("delete from problem where id = ?", problemId);
        return ApiResponse.success(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Resource> exportProblems(List<String> problemIdParams, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return jsonErrorAsBinary(permissionContext.errorResponse());
        }

        List<Long> problemIds = normalizeProblemIds(problemIdParams);
        if (problemIds.isEmpty()) {
            return jsonErrorAsBinary(ApiResponse.error("invalid-problem_id", "problem_id: This list may not be empty."));
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutput = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                int index = 1;
                for (Long problemId : problemIds) {
                    ApiResponse<Object> detail = adminProblemQueryService.getAdminProblems(
                            Map.of("id", String.valueOf(problemId)),
                            authentication
                    );
                    if (detail.error() != null) {
                        return jsonErrorAsBinary(detail);
                    }
                    if (!(detail.data() instanceof Map<?, ?> rawProblemMap)) {
                        return jsonErrorAsBinary(ApiResponse.error("error", "server error"));
                    }
                    Map<String, Object> problem = (Map<String, Object>) rawProblemMap;
                    Map<String, Object> exportProblem = buildExportProblem(problem);
                    writeZipEntry(
                            zipOutput,
                            index + "/problem.json",
                            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(exportProblem)
                    );
                    writeProblemTestCasesForExport(zipOutput, problem, index);
                    index++;
                }
            }

            byte[] content = output.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=problem-export.zip")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(content.length)
                    .body(new ByteArrayResource(content));
        } catch (IOException exception) {
            return jsonErrorAsBinary(ApiResponse.error("error", "server error"));
        }
    }

    @Override
    public ApiResponse<Object> importProblems(MultipartFile file, String autoKc, String languagePackIdParam, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        Long languagePackId = parseLanguagePackId(languagePackIdParam);
        ensureLanguagePackAccess(permissionContext, languagePackId);
        if (file == null || file.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        }

        Path tempZip;
        try {
            tempZip = Files.createTempFile("alethicode-problem-import-", ".zip");
            file.transferTo(tempZip);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        }

        try (ZipFile zipFile = new ZipFile(tempZip.toFile(), StandardCharsets.UTF_8)) {
            List<Integer> problemIndexes = extractImportProblemIndexes(zipFile);
            if (problemIndexes.isEmpty()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid problem format, error is missing problem.json");
            }

            int importCount = 0;
            for (Integer index : problemIndexes) {
                String problemJsonEntry = index + "/problem.json";
                ZipEntry entry = zipFile.getEntry(problemJsonEntry);
                if (entry == null) {
                    throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid problem format, error is missing " + problemJsonEntry);
                }

                Map<String, Object> problemData;
                try (InputStream input = zipFile.getInputStream(entry)) {
                    problemData = objectMapper.readValue(input, new TypeReference<>() {
                    });
                }

                ImportedProblemPackage importedProblemPackage = buildImportProblemPackage(
                        problemData,
                        loadImportTestCasesFromZip(zipFile, index + "/testcase/", isSpj(problemData.get("spj")))
                );
                writeImportedProblem(importedProblemPackage, languagePackId, authentication);
                importCount++;
            }

            return ApiResponse.success(Map.of(
                    "import_count", importCount,
                    "kc_bindcount", 0,
                    "kc_auto_bindcount", 0
            ));
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Upload failed");
        } catch (ImportProblemException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public ApiResponse<Object> importFps(MultipartFile file, Authentication authentication) {
        PermissionContext permissionContext = resolvePermission(authentication);
        if (permissionContext.errorResponse() != null) {
            return permissionContext.errorResponse();
        }
        Long languagePackId = resolveDefaultLanguagePackForImport(permissionContext);
        ensureLanguagePackAccess(permissionContext, languagePackId);
        if (file == null || file.isEmpty()) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parse upload file error");
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("alethicode-fps-import-", ".xml");
            file.transferTo(tempFile);
        } catch (IOException exception) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Parse upload file error");
        }

        try {
            List<FpsProblem> problems = parseFpsProblems(tempFile);
            int importCount = 0;
            for (FpsProblem problem : problems) {
                FpsProblem normalized = saveFpsImages(problem);
                writeImportedProblem(buildFpsProblemPackage(normalized), languagePackId, authentication);
                importCount++;
            }
            return ApiResponse.success(Map.of("import_count", importCount));
        } catch (IOException | ParserConfigurationException | SAXException | ImportProblemException exception) {
            String message = exception instanceof ImportProblemException
                    ? exception.getMessage()
                    : "Parse upload file error";
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", message);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    private List<Long> normalizeProblemIds(List<String> problemIdParams) {
        if (problemIdParams == null || problemIdParams.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String raw : problemIdParams) {
            String normalized = trimToNull(raw);
            if (normalized == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(normalized));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildExportProblem(Map<String, Object> problem) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("display_id", String.valueOf(problem.get("_id")));
        export.put("title", String.valueOf(problem.get("title")));
        export.put("description", htmlValue(problem.get("description")));
        export.put("input_description", htmlValue(problem.get("input_description")));
        export.put("output_description", htmlValue(problem.get("output_description")));
        export.put("hint", htmlValue(problem.get("hint")));
        export.put("test_case_score", normalizeScoreList(problem.get("test_case_score")));
        export.put("time_limit", toInt(problem.get("time_limit"), 1000));
        export.put("memory_limit", toInt(problem.get("memory_limit"), 256));
        export.put("difficulty", asString(problem.get("difficulty")));
        export.put("samples", toSampleList(problem.get("samples")));
        export.put("template", parseTemplateMap(problem.get("template")));
        export.put("spj", resolveProblemSpj(problem));
        String source = trimToNull(asString(problem.get("source")));
        if (source == null) {
            source = properties.getWebsite().getName() + " " + properties.getWebsite().getBaseUrl();
        }
        export.put("source", source);
        export.put("reference_solution_language", asString(problem.get("reference_solution_language")));
        export.put("reference_solution_code", asString(problem.get("reference_solution_code")));
        export.put("answers", List.of());
        export.put("tags", toStringList(problem.get("tags")));
        export.put("kc", List.of());
        return export;
    }

    private void writeProblemTestCasesForExport(ZipOutputStream zipOutput, Map<String, Object> problem, int index)
            throws IOException {
        String testCaseId = trimToNull(asString(problem.get("test_case_id")));
        if (testCaseId == null) {
            throw new IOException("missing test_case_id");
        }
        Path caseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        Path infoPath = caseDir.resolve("info");
        if (!Files.isRegularFile(infoPath)) {
            throw new IOException("missing testcase info");
        }
        Map<String, Object> info = objectMapper.readValue(Files.readString(infoPath), new TypeReference<>() {
        });
        Object testCases = info.get("test_cases");
        if (!(testCases instanceof Map<?, ?> map)) {
            throw new IOException("invalid testcase info");
        }
        for (Object value : map.values()) {
            if (!(value instanceof Map<?, ?> item)) {
                continue;
            }
            String inputName = trimToNull(asString(item.get("input_name")));
            if (inputName == null) {
                continue;
            }
            Path inputPath = caseDir.resolve(inputName);
            if (!Files.isRegularFile(inputPath)) {
                throw new IOException("missing testcase input file");
            }
            writeZipEntry(zipOutput, index + "/testcase/" + inputName, Files.readAllBytes(inputPath));

            String outputName = trimToNull(asString(item.get("output_name")));
            if (outputName == null) {
                continue;
            }
            Path outputPath = caseDir.resolve(outputName);
            if (!Files.isRegularFile(outputPath)) {
                throw new IOException("missing testcase output file");
            }
            writeZipEntry(zipOutput, index + "/testcase/" + outputName, Files.readAllBytes(outputPath));
        }
    }

    private boolean resolveProblemSpj(Map<String, Object> problem) {
        String testCaseId = trimToNull(asString(problem.get("test_case_id")));
        if (testCaseId == null) {
            return false;
        }
        Path infoPath = Path.of(properties.getSystem().getTestCaseDir(), testCaseId, "info");
        if (!Files.isRegularFile(infoPath)) {
            return false;
        }
        try {
            Map<String, Object> info = objectMapper.readValue(Files.readString(infoPath), new TypeReference<>() {
            });
            Object spj = info.get("spj");
            return spj instanceof Boolean booleanValue && booleanValue;
        } catch (IOException exception) {
            return false;
        }
    }

    private void writeZipEntry(ZipOutputStream zipOutput, String name, byte[] content) throws IOException {
        zipOutput.putNextEntry(new ZipEntry(name));
        zipOutput.write(content);
        zipOutput.closeEntry();
    }

    private List<Integer> extractImportProblemIndexes(ZipFile zipFile) {
        Pattern problemPattern = Pattern.compile("^(\\d+)/problem\\.json$");
        return zipFile.stream()
                .map(ZipEntry::getName)
                .map(problemPattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private ImportTestCaseResult importTestCasesFromZip(ZipFile zipFile, String dir, boolean spj) {
        List<String> names = zipFile.stream().map(ZipEntry::getName).toList();
        List<String> testCaseFiles = filterImportTestCaseNames(names, spj, dir);
        if (testCaseFiles.isEmpty()) {
            throw new ImportProblemException("Empty file");
        }

        String testCaseId = randomString(32);
        Path testCaseDir = Path.of(properties.getSystem().getTestCaseDir(), testCaseId);
        try {
            Files.createDirectories(testCaseDir);
            Map<String, Integer> sizeCache = new HashMap<>();
            Map<String, String> md5Cache = new HashMap<>();

            for (String filename : testCaseFiles) {
                ZipEntry entry = zipFile.getEntry(dir + filename);
                if (entry == null) {
                    throw new ImportProblemException("Bad zip file");
                }
                byte[] content = readZipEntry(zipFile, entry);
                content = normalizeLineEnding(content);
                sizeCache.put(filename, content.length);
                if (filename.endsWith(".out")) {
                    md5Cache.put(filename, md5Hex(rstripWhitespace(content)));
                }
                Files.write(testCaseDir.resolve(filename), content);
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("spj", spj);
            Map<String, Object> testCases = new LinkedHashMap<>();
            info.put("test_cases", testCases);
            List<Map<String, Object>> scoreInfo = new ArrayList<>();
            if (spj) {
                for (int i = 0; i < testCaseFiles.size(); i++) {
                    String input = testCaseFiles.get(i);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("input_name", input);
                    item.put("input_size", sizeCache.getOrDefault(input, 0));
                    testCases.put(String.valueOf(i + 1), item);
                }
            } else {
                for (int i = 0; i + 1 < testCaseFiles.size(); i += 2) {
                    String input = testCaseFiles.get(i);
                    String output = testCaseFiles.get(i + 1);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("stripped_output_md5", md5Cache.getOrDefault(output, ""));
                    item.put("input_size", sizeCache.getOrDefault(input, 0));
                    item.put("output_size", sizeCache.getOrDefault(output, 0));
                    item.put("input_name", input);
                    item.put("output_name", output);
                    testCases.put(String.valueOf((i / 2) + 1), item);
                    scoreInfo.add(Map.of(
                            "input_name", input,
                            "output_name", output,
                            "score", 100
                    ));
                }
            }

            Files.writeString(
                    testCaseDir.resolve("info"),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info),
                    StandardCharsets.UTF_8
            );
            return new ImportTestCaseResult(testCaseId, scoreInfo);
        } catch (IOException exception) {
            throw new ImportProblemException("Bad zip file");
        }
    }

    private List<String> filterImportTestCaseNames(List<String> names, boolean spj, String dir) {
        Set<String> nameSet = Set.copyOf(names);
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

    @SuppressWarnings("unchecked")
    private List<ProblemTestCaseScoreRequest> buildImportTestCaseScore(
            Map<String, Object> problemData,
            List<Map<String, Object>> fallback,
            boolean spj
    ) {
        Object scoreObject = problemData.get("test_case_score");
        if (!(scoreObject instanceof List<?> list) || list.isEmpty()) {
            return fallback.stream()
                    .map(item -> new ProblemTestCaseScoreRequest(
                            asString(item.get("input_name")),
                            spj ? null : asString(item.get("output_name")),
                            toInt(item.get("score"), 100)
                    ))
                    .toList();
        }
        List<ProblemTestCaseScoreRequest> scores = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String input = trimToNull(asString(map.get("input_name")));
            String output = trimToNull(asString(map.get("output_name")));
            if (input == null || (!spj && output == null)) {
                continue;
            }
            scores.add(new ProblemTestCaseScoreRequest(input, spj ? null : output, toInt(map.get("score"), 100)));
        }
        return scores;
    }

    @SuppressWarnings("unchecked")
    private ImportedProblemPackage buildImportProblemPackage(
            Map<String, Object> problemData,
            ImportedZipTestCases importedZipTestCases
    ) {
        String displayId = trimToNull(asString(problemData.get("display_id")));
        if (displayId == null) {
            throw new ImportProblemException("Invalid problem format, error is display_id is required");
        }
        displayId = displayId.length() > 24 ? displayId.substring(0, 24) : displayId;

        String title = trimToNull(asString(problemData.get("title")));
        if (title == null) {
            throw new ImportProblemException("Invalid problem format, error is title is required");
        }

        List<ProblemSampleRequest> samples = new ArrayList<>();
        Object samplesObject = problemData.get("samples");
        if (samplesObject instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String input = asString(map.get("input"));
                String output = asString(map.get("output"));
                if (trimToNull(input) == null || trimToNull(output) == null) {
                    continue;
                }
                samples.add(new ProblemSampleRequest(input, output));
            }
        }
        if (samples.isEmpty()) {
            throw new ImportProblemException("Invalid problem format, error is samples is required");
        }

        Map<String, String> template = new LinkedHashMap<>();
        Object templateObject = problemData.get("template");
        if (templateObject instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String lang = asString(entry.getKey());
                if (trimToNull(lang) == null) {
                    continue;
                }
                String value = buildTemplateFromExportEntry(entry.getValue());
                template.put(lang, value);
            }
        }
        if (template.isEmpty()) {
            throw new ImportProblemException("Invalid problem format, error is template is required");
        }

        List<LanguagePackProblemPackage.Sample> packageSamples = samples.stream()
                .map(sample -> new LanguagePackProblemPackage.Sample(sample.input(), sample.output()))
                .toList();
        List<Map<String, Object>> score = toTestCaseScoreMaps(
                buildImportTestCaseScore(problemData, importedZipTestCases.scoreFallback(), importedZipTestCases.spj())
        );
        return new ImportedProblemPackage(
                new LanguagePackProblemPackage(
                        displayId,
                        title,
                        extractHtmlValue(problemData, "description"),
                        extractHtmlValue(problemData, "input_description"),
                        extractHtmlValue(problemData, "output_description"),
                        packageSamples,
                        importedZipTestCases.testCases(),
                        template,
                        toInt(problemData.get("time_limit"), 1000),
                        toInt(problemData.get("memory_limit"), 256),
                        normalizeDifficultyValue(problemData.get("difficulty")),
                        List.of(),
                        List.of(),
                        List.of(),
                        "",
                        List.of(),
                        null,
                        trimToNull(asString(problemData.get("reference_solution_language"))),
                        asString(problemData.get("reference_solution_code"))
                ),
                normalizeImportedTags(toStringList(problemData.get("tags"))),
                score,
                importedZipTestCases.spj(),
                asString(problemData.get("source"))
        );
    }

    private ImportedProblemPackage buildFpsProblemPackage(FpsProblem problem) {
        Map<String, String> template = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : problem.template().entrySet()) {
            String fpsLang = entry.getKey();
            String lang = "Python".equals(fpsLang) ? "Python3" : fpsLang;
            String prepend = problem.prepend().getOrDefault(fpsLang, "");
            String append = problem.append().getOrDefault(fpsLang, "");
            template.put(
                    lang,
                    "//PREPEND BEGIN\n"
                            + prepend
                            + "\n//PREPEND END\n\n//TEMPLATE BEGIN\n"
                            + entry.getValue()
                            + "\n//TEMPLATE END\n\n//APPEND BEGIN\n"
                            + append
                            + "\n//APPEND END"
            );
        }
        if (template.isEmpty()) {
            template.put("Python3", "//PREPEND BEGIN\n\n//PREPEND END\n\n//TEMPLATE BEGIN\nprint(1)\n//TEMPLATE END\n\n//APPEND BEGIN\n\n//APPEND END");
        }

        List<LanguagePackProblemPackage.Sample> samples = problem.samples().stream()
                .map(sample -> new LanguagePackProblemPackage.Sample(sample.input(), sample.output()))
                .toList();
        if (samples.isEmpty()) {
            samples = List.of(new LanguagePackProblemPackage.Sample("", ""));
        }
        List<LanguagePackProblemPackage.TestCase> testCases = problem.testCases().stream()
                .map(testCase -> new LanguagePackProblemPackage.TestCase(
                        testCase.input(),
                        problem.spj() ? null : testCase.output()
                ))
                .toList();

        return new ImportedProblemPackage(
                new LanguagePackProblemPackage(
                        "fps-" + randomString(4),
                        trimToNull(problem.title()) == null ? "FPS Imported Problem" : problem.title(),
                        problem.description(),
                        problem.input(),
                        problem.output(),
                        samples,
                        testCases,
                        template,
                        problem.timeLimitMs(),
                        problem.memoryLimitMb(),
                        "Mid",
                        List.of(),
                        List.of(),
                        List.of(),
                        "",
                        List.of(),
                        null,
                        null,
                        null
                ),
                List.of("type:coding"),
                List.of(),
                problem.spj(),
                problem.source()
        );
    }

    private void writeImportedProblem(ImportedProblemPackage importedProblemPackage, Long languagePackId, Authentication authentication) {
        Long createdById = findUserIdByUsername(authentication.getName());
        if (createdById == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("permission-denied", "请先登录");
        }
        String displayId = trimToNull(importedProblemPackage.problemPackage().displayId());
        if (displayId == null) {
            throw new ImportProblemException("Invalid problem format, error is display_id is required");
        }
        if (existsDisplayId(displayId, null)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Display ID already exists");
        }
        ProblemPackageWriteResult writeResult = problemPackageWriteService.writeProblem(
                importedProblemPackage.problemPackage(),
                new ProblemPackageWriteOptions(
                        createdById,
                        false,
                        false,
                        false,
                        importedProblemPackage.spj(),
                        "class_private",
                        importedProblemPackage.source(),
                        normalizeStatisticInfo(Map.of()),
                        importedProblemPackage.testCaseScore(),
                        importedProblemPackage.tags()
                )
        );
        if (writeResult.problemId() == null) {
            throw new ImportProblemException("Import failed");
        }
        upsertLanguagePackMapping(languagePackId, writeResult.problemId());
    }

    private List<String> normalizeImportedTags(List<String> tags) {
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String rawTag : tags) {
            String tag = trimToNull(rawTag);
            if (tag == null || QUESTION_TYPE_TAGS.contains(tag)) {
                continue;
            }
            normalized.add(tag);
        }
        normalized.add("type:coding");
        return List.copyOf(normalized);
    }

    private List<Map<String, Object>> toTestCaseScoreMaps(List<ProblemTestCaseScoreRequest> scoreRequests) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProblemTestCaseScoreRequest scoreRequest : scoreRequests) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("input_name", scoreRequest.inputName());
            item.put("output_name", scoreRequest.outputName());
            item.put("score", scoreRequest.score());
            result.add(item);
        }
        return result;
    }

    private String normalizeDifficultyValue(Object raw) {
        String difficulty = trimToNull(asString(raw));
        if (difficulty == null) {
            throw new ImportProblemException("Invalid problem format, error is difficulty is required");
        }
        if (!ALLOWED_DIFFICULTY.contains(difficulty)) {
            throw new ImportProblemException("Invalid problem format, error is difficulty is invalid");
        }
        return difficulty;
    }

    private boolean isSpj(Object raw) {
        if (raw instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = trimToNull(asString(raw));
        return normalized != null && !"false".equalsIgnoreCase(normalized);
    }

    private ImportedZipTestCases loadImportTestCasesFromZip(ZipFile zipFile, String dir, boolean spj) {
        List<String> names = zipFile.stream().map(ZipEntry::getName).toList();
        List<String> testCaseFiles = filterImportTestCaseNames(names, spj, dir);
        if (testCaseFiles.isEmpty()) {
            throw new ImportProblemException("Empty file");
        }

        List<LanguagePackProblemPackage.TestCase> testCases = new ArrayList<>();
        List<Map<String, Object>> fallbackScore = new ArrayList<>();
        try {
            if (spj) {
                for (String inputName : testCaseFiles) {
                    ZipEntry inputEntry = zipFile.getEntry(dir + inputName);
                    if (inputEntry == null) {
                        throw new ImportProblemException("Bad zip file");
                    }
                    String input = new String(normalizeLineEnding(readZipEntry(zipFile, inputEntry)), StandardCharsets.UTF_8);
                    testCases.add(new LanguagePackProblemPackage.TestCase(input, null));
                    fallbackScore.add(Map.of("input_name", inputName, "output_name", "", "score", 100));
                }
            } else {
                for (int i = 0; i + 1 < testCaseFiles.size(); i += 2) {
                    String inputName = testCaseFiles.get(i);
                    String outputName = testCaseFiles.get(i + 1);
                    ZipEntry inputEntry = zipFile.getEntry(dir + inputName);
                    ZipEntry outputEntry = zipFile.getEntry(dir + outputName);
                    if (inputEntry == null || outputEntry == null) {
                        throw new ImportProblemException("Bad zip file");
                    }
                    String input = new String(normalizeLineEnding(readZipEntry(zipFile, inputEntry)), StandardCharsets.UTF_8);
                    String output = new String(normalizeLineEnding(readZipEntry(zipFile, outputEntry)), StandardCharsets.UTF_8);
                    testCases.add(new LanguagePackProblemPackage.TestCase(input, output));
                    fallbackScore.add(Map.of("input_name", inputName, "output_name", outputName, "score", 100));
                }
            }
        } catch (IOException exception) {
            throw new ImportProblemException("Bad zip file");
        }
        return new ImportedZipTestCases(testCases, fallbackScore, spj);
    }

    private ResponseEntity<Resource> jsonErrorAsBinary(ApiResponse<Object> response) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(response);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ByteArrayResource(payload));
        } catch (JsonProcessingException exception) {
            byte[] fallback = "{\"error\":\"error\",\"data\":\"server error\"}".getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ByteArrayResource(fallback));
        }
    }

    private Map<String, Object> htmlValue(Object raw) {
        return Map.of(
                "format", "html",
                "value", asString(raw)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeScoreList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String input = trimToNull(asString(map.get("input_name")));
            String output = trimToNull(asString(map.get("output_name")));
            if (input == null) {
                continue;
            }
            LinkedHashMap<String, Object> scoreRow = new LinkedHashMap<>();
            scoreRow.put("score", toInt(map.get("score"), 100));
            scoreRow.put("input_name", input);
            scoreRow.put("output_name", output);
            result.add(scoreRow);
        }
        return result;
    }

    private List<Map<String, String>> toSampleList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, String>> samples = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String input = asString(map.get("input"));
            String output = asString(map.get("output"));
            samples.add(Map.of("input", input, "output", output));
        }
        return samples;
    }

    private Map<String, Object> parseTemplateMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String lang = asString(entry.getKey());
            String templateRaw = asString(entry.getValue());
            Matcher matcher = TEMPLATE_BLOCK_PATTERN.matcher(templateRaw);
            if (matcher.matches()) {
                parsed.put(lang, Map.of(
                        "prepend", matcher.group(1),
                        "template", matcher.group(2),
                        "append", matcher.group(3)
                ));
            } else {
                parsed.put(lang, Map.of(
                        "prepend", "",
                        "template", templateRaw,
                        "append", ""
                ));
            }
        }
        return parsed;
    }

    private String buildTemplateFromExportEntry(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return asString(raw);
        }
        String prepend = asString(map.get("prepend"));
        String template = asString(map.get("template"));
        String append = asString(map.get("append"));
        return "//PREPEND BEGIN\n"
                + prepend
                + "\n//PREPEND END\n\n//TEMPLATE BEGIN\n"
                + template
                + "\n//TEMPLATE END\n\n//APPEND BEGIN\n"
                + append
                + "\n//APPEND END";
    }

    private String extractHtmlValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Map<?, ?> map) {
            Object raw = map.get("value");
            return asString(raw);
        }
        return asString(value);
    }

    private List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            String value = trimToNull(asString(item));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private int toInt(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String asString(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private byte[] readZipEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
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
            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm not available", exception);
        }
    }

    private String randomString(int length) {
        final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private List<FpsProblem> parseFpsProblems(Path fpsPath)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        Document document = factory.newDocumentBuilder().parse(fpsPath.toFile());
        Element root = document.getDocumentElement();
        String version = trimToNull(root.getAttribute("version"));
        if (version == null) {
            version = "No Version";
        }
        if (!"1.1".equals(version) && !"1.2".equals(version)) {
            throw new ImportProblemException("Parse upload file error");
        }

        List<FpsProblem> problems = new ArrayList<>();
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!"item".equals(node.getNodeName())) {
                continue;
            }
            problems.add(parseFpsProblem((Element) node));
        }
        return problems;
    }

    private FpsProblem parseFpsProblem(Element node) {
        String title = "No Title";
        String description = "No Description";
        String input = "No Input Description";
        String output = "No Output Description";
        String hint = "";
        String source = "";
        int timeLimitMs = 1000;
        int memoryLimitMb = 256;

        Map<String, String> template = new LinkedHashMap<>();
        Map<String, String> prepend = new LinkedHashMap<>();
        Map<String, String> append = new LinkedHashMap<>();
        List<FpsSample> samples = new ArrayList<>();
        List<FpsTestCase> testCases = new ArrayList<>();
        List<FpsImage> images = new ArrayList<>();
        boolean spj = false;

        String pendingSampleInput = null;
        String pendingTestInput = null;

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tag = element.getTagName();
            String text = element.getTextContent() == null ? "" : element.getTextContent();
            switch (tag) {
                case "title" -> title = text;
                case "description" -> description = text;
                case "input" -> input = text;
                case "output" -> output = text;
                case "hint" -> hint = text;
                case "source" -> source = text;
                case "time_limit" -> {
                    String unit = trimToNull(element.getAttribute("unit"));
                    if (unit == null) {
                        unit = "s";
                    }
                    int value = parsePositiveInt(text, "Parse FPS file error: Invalid time limit value");
                    if ("s".equals(unit)) {
                        timeLimitMs = value * 1000;
                    } else if ("ms".equals(unit)) {
                        timeLimitMs = value;
                    } else {
                        throw new ImportProblemException("Parse FPS file error: Invalid time limit unit");
                    }
                }
                case "memory_limit" -> {
                    String unit = trimToNull(element.getAttribute("unit"));
                    if (unit == null) {
                        unit = "MB";
                    }
                    int value = parsePositiveInt(text, "Parse FPS file error: Invalid memory limit value");
                    String upper = unit.toUpperCase();
                    if ("MB".equals(upper)) {
                        memoryLimitMb = value;
                    } else {
                        throw new ImportProblemException("Parse FPS file error: Invalid memory limit unit");
                    }
                }
                case "template" -> {
                    String language = trimToNull(element.getAttribute("language"));
                    if (language == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid template language");
                    }
                    template.put(language, text);
                }
                case "prepend" -> {
                    String language = trimToNull(element.getAttribute("language"));
                    if (language == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid prepend language");
                    }
                    prepend.put(language, text);
                }
                case "append" -> {
                    String language = trimToNull(element.getAttribute("language"));
                    if (language == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid append language");
                    }
                    append.put(language, text);
                }
                case "spj" -> {
                    String language = trimToNull(element.getAttribute("language"));
                    if (language == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid spj, language name if missed");
                    }
                    spj = true;
                }
                case "img" -> {
                    String src = "";
                    byte[] blob = new byte[0];
                    NodeList imgChildren = element.getChildNodes();
                    for (int j = 0; j < imgChildren.getLength(); j++) {
                        Node imgNode = imgChildren.item(j);
                        if (imgNode.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element imgElement = (Element) imgNode;
                        if ("src".equals(imgElement.getTagName())) {
                            src = imgElement.getTextContent() == null ? "" : imgElement.getTextContent();
                        } else if ("base64".equals(imgElement.getTagName())) {
                            String encoded = imgElement.getTextContent() == null ? "" : imgElement.getTextContent();
                            blob = Base64.getDecoder().decode(encoded);
                        }
                    }
                    images.add(new FpsImage(src, blob));
                }
                case "sample_input" -> {
                    if (pendingSampleInput != null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid sample_input order");
                    }
                    pendingSampleInput = text;
                }
                case "sample_output" -> {
                    if (pendingSampleInput == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid sample_output order");
                    }
                    samples.add(new FpsSample(pendingSampleInput, text));
                    pendingSampleInput = null;
                }
                case "test_input" -> {
                    if (pendingTestInput != null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid test_input order");
                    }
                    pendingTestInput = text;
                }
                case "test_output" -> {
                    if (pendingTestInput == null) {
                        throw new ImportProblemException("Parse FPS file error: Invalid test_output order");
                    }
                    testCases.add(new FpsTestCase(pendingTestInput, text));
                    pendingTestInput = null;
                }
                default -> {
                }
            }
        }

        if (pendingSampleInput != null || pendingTestInput != null) {
            throw new ImportProblemException("Parse FPS file error: Invalid FPS tag order");
        }
        if (testCases.isEmpty()) {
            throw new ImportProblemException("Parse FPS file error: test_cases is required");
        }
        return new FpsProblem(
                title,
                description,
                input,
                output,
                hint,
                source,
                timeLimitMs,
                memoryLimitMb,
                template,
                prepend,
                append,
                samples,
                testCases,
                images,
                spj
        );
    }

    private List<ProblemTestCaseScoreRequest> saveFpsTestCases(FpsProblem problem, Path testCaseDir) throws IOException {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("spj", problem.spj());
        Map<String, Object> testCases = new LinkedHashMap<>();
        info.put("test_cases", testCases);

        List<ProblemTestCaseScoreRequest> score = new ArrayList<>();
        for (int i = 0; i < problem.testCases().size(); i++) {
            FpsTestCase testCase = problem.testCases().get(i);
            String inputName = (i + 1) + ".in";
            String outputName = (i + 1) + ".out";
            byte[] inputBytes = normalizeLineEnding(testCase.input().getBytes(StandardCharsets.UTF_8));
            Files.write(testCaseDir.resolve(inputName), inputBytes);
            String outputText = testCase.output() == null ? "" : testCase.output();
            if (!outputText.isEmpty()) {
                byte[] outputBytes = normalizeLineEnding(outputText.getBytes(StandardCharsets.UTF_8));
                Files.write(testCaseDir.resolve(outputName), outputBytes);
            }

            Map<String, Object> oneInfo = new LinkedHashMap<>();
            oneInfo.put("input_size", inputBytes.length);
            oneInfo.put("input_name", inputName);

            if (!problem.spj()) {
                byte[] outputBytes = normalizeLineEnding(outputText.getBytes(StandardCharsets.UTF_8));
                oneInfo.put("output_size", outputBytes.length);
                oneInfo.put("output_name", outputName);
                oneInfo.put("stripped_output_md5", md5Hex(rstripWhitespace(outputBytes)));
            }
            testCases.put(String.valueOf(i + 1), oneInfo);
            score.add(new ProblemTestCaseScoreRequest(inputName, problem.spj() ? null : outputName, 0));
        }

        Files.writeString(
                testCaseDir.resolve("info"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info),
                StandardCharsets.UTF_8
        );
        return score;
    }

    private FpsProblem saveFpsImages(FpsProblem problem) throws IOException {
        if (problem.images().isEmpty()) {
            return problem;
        }
        Path uploadDir = Path.of(properties.getSystem().getUploadDir());
        Files.createDirectories(uploadDir);
        String uploadPrefix = trimToNull(properties.getSystem().getUploadPrefix());
        if (uploadPrefix == null) {
            uploadPrefix = "/public/upload";
        }

        String description = problem.description();
        String input = problem.input();
        String output = problem.output();

        for (FpsImage image : problem.images()) {
            String ext = "";
            String src = image.src();
            int dotIndex = src == null ? -1 : src.lastIndexOf('.');
            if (dotIndex >= 0) {
                ext = src.substring(dotIndex);
            }
            String filename = randomString(12) + ext;
            Files.write(uploadDir.resolve(filename), image.blob());
            String fileUrl = uploadPrefix + "/" + filename;
            if (trimToNull(src) != null) {
                description = description.replace(src, fileUrl);
                input = input.replace(src, fileUrl);
                output = output.replace(src, fileUrl);
            }
        }

        return problem.withDescription(description).withInput(input).withOutput(output);
    }

    private AdminProblemUpsertRequest buildFpsImportRequest(
            FpsProblem problem,
            String testCaseId,
            List<ProblemTestCaseScoreRequest> score
    ) {
        Map<String, String> template = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : problem.template().entrySet()) {
            String fpsLang = entry.getKey();
            String lang = "Python".equals(fpsLang) ? "Python3" : fpsLang;
            String prepend = problem.prepend().getOrDefault(fpsLang, "");
            String append = problem.append().getOrDefault(fpsLang, "");
            template.put(
                    lang,
                    "//PREPEND BEGIN\n"
                            + prepend
                            + "\n//PREPEND END\n\n//TEMPLATE BEGIN\n"
                            + entry.getValue()
                            + "\n//TEMPLATE END\n\n//APPEND BEGIN\n"
                            + append
                            + "\n//APPEND END"
            );
        }
        if (template.isEmpty()) {
            template.put("Python3", "//PREPEND BEGIN\n\n//PREPEND END\n\n//TEMPLATE BEGIN\nprint(1)\n//TEMPLATE END\n\n//APPEND BEGIN\n\n//APPEND END");
        }

        List<ProblemSampleRequest> samples = problem.samples().stream()
                .map(sample -> new ProblemSampleRequest(sample.input(), sample.output()))
                .toList();
        if (samples.isEmpty()) {
            samples = List.of(new ProblemSampleRequest("", ""));
        }

        return new AdminProblemUpsertRequest(
                null,
                "fps-" + randomString(4),
                null,
                trimToNull(problem.title()) == null ? "FPS Imported Problem" : problem.title(),
                problem.description(),
                problem.input(),
                problem.output(),
                samples,
                testCaseId,
                score,
                problem.timeLimitMs(),
                problem.memoryLimitMb(),
                new ArrayList<>(properties.getLanguage().getLanguages()),
                template,
                null,
                null,
                false,
                "Mid",
                List.of("type:coding"),
                problem.hint(),
                problem.source(),
                Map.of()
        );
    }

    private int parsePositiveInt(String raw, String errorMessage) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ImportProblemException(errorMessage);
        }
    }

    private Long parseLanguagePackId(String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Invalid language_pack_id");
        }
    }

    private Long normalizeLanguagePackId(Long rawValue) {
        if (rawValue == null || rawValue <= 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        return rawValue;
    }

    private void ensureLanguagePackAccess(PermissionContext permissionContext, Long languagePackId) {
        if (languagePackId == null || languagePackId <= 0) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
        }
        if (!permissionContext.teacher() || permissionContext.canManageAllProblems()) {
            return;
        }
        if (!permissionContext.accessibleLanguagePackIds().contains(languagePackId)) {
            throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "Permission denied");
        }
    }

    private Long resolveDefaultLanguagePackForImport(PermissionContext permissionContext) {
        if (permissionContext.teacher() && !permissionContext.canManageAllProblems()) {
            if (permissionContext.accessibleLanguagePackIds().isEmpty()) {
                throw com.alethicode.exception.BusinessExceptions.fromLegacy("error", "language_pack_id is required");
            }
            return permissionContext.accessibleLanguagePackIds().stream().min(Long::compareTo).orElse(null);
        }
        return jdbcTemplate.query(
                """
                select id
                from language_pack
                where status = 'published'
                order by update_time desc, id asc
                limit 1
                """,
                rs -> rs.next() ? rs.getLong("id") : null
        );
    }

    private boolean canManageProblem(PermissionContext permissionContext,
                                     Long problemId,
                                     String actorUsername,
                                     String createdByUsername) {
        if (permissionContext.canManageAllProblems()) {
            return true;
        }
        if (permissionContext.teacher()) {
            return isProblemInAccessibleLanguagePacks(problemId, permissionContext.accessibleLanguagePackIds());
        }
        return actorUsername != null && actorUsername.equals(createdByUsername);
    }

    private boolean isProblemInAccessibleLanguagePacks(Long problemId, Set<Long> accessibleLanguagePackIds) {
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

    private void upsertLanguagePackMapping(Long languagePackId, Long problemId) {
        jdbcTemplate.update(
                """
                insert into language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
                values (?, ?, null, now())
                on conflict do nothing
                """,
                languagePackId,
                problemId
        );
    }

    private PermissionContext resolvePermission(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return new PermissionContext(false, false, Set.of(), ApiResponse.error("permission-denied", "请先登录"));
        }

        String username = authentication.getName();
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
                        boolean canManageAll = "Admin".equals(adminType) || teacher || "All".equals(problemPermission);
                        Set<Long> accessibleLanguagePackIds = teacher ? loadTeacherLanguagePackIds(userId) : Set.of();
                        return new PermissionContext(canManageAll, teacher, accessibleLanguagePackIds, null);
                    },
                    username
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

    private Long findUserIdByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id from \"user\" where username = ?",
                    Long.class,
                    username
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private OwnershipContext findOwnership(Long problemId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    select p.id, u.username as created_by_username
                    from problem p
                    left join "user" u on u.id = p.created_by_id
                    where p.id = ?
                    """,
                    (rs, rowNum) -> new OwnershipContext(
                            true,
                            rs.getString("created_by_username")
                    ),
                    problemId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return new OwnershipContext(false, null);
        }
    }

    private boolean existsDisplayId(String displayId, Long excludeId) {
        if (excludeId == null) {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from problem where _id = ?",
                    Long.class,
                    displayId
            );
            return count != null && count > 0;
        }
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from problem where _id = ? and id <> ?",
                Long.class,
                displayId,
                excludeId
        );
        return count != null && count > 0;
    }

    private String checkTestCaseFilenames(String testCaseId, List<ProblemTestCaseScoreRequest> testCaseScore) {
        if (trimToNull(testCaseId) == null || testCaseScore == null || testCaseScore.isEmpty()) {
            return null;
        }
        Path infoPath = Path.of(properties.getSystem().getTestCaseDir(), testCaseId, "info");
        if (!Files.isRegularFile(infoPath)) {
            return null;
        }
        try {
            Map<String, Object> info = objectMapper.readValue(Files.readString(infoPath), new TypeReference<>() {
            });
            Object testCases = info.get("test_cases");
            if (!(testCases instanceof Map<?, ?> map)) {
                return null;
            }

            Set<String> validInputs = map.values().stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> (Map<?, ?>) item)
                    .map(item -> item.get("input_name"))
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.toSet());

            for (ProblemTestCaseScoreRequest item : testCaseScore) {
                String inputName = item.inputName();
                if (inputName != null && !validInputs.contains(inputName)) {
                    return "Test case file '" + inputName + "' does not exist in uploaded test cases";
                }
            }
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    private void replaceProblemTags(Long problemId, List<String> tags, boolean ensureCodingQuestionType) {
        jdbcTemplate.update("delete from problem_problem_tags where problem_id = ?", problemId);
        for (String rawTag : tags) {
            String tag = trimToNull(rawTag);
            if (tag == null) {
                continue;
            }
            long tagId = findOrCreateTagId(tag);
            jdbcTemplate.update(
                    "insert into problem_problem_tags(problem_id, problemtag_id) values (?, ?) on conflict do nothing",
                    problemId,
                    tagId
            );
        }

        if (!ensureCodingQuestionType) {
            return;
        }

        jdbcTemplate.update(
                """
                delete from problem_problem_tags ppt
                using problem_tag t
                where ppt.problemtag_id = t.id
                  and ppt.problem_id = ?
                  and t.name in ('type:coding', 'type:choice', 'type:fill_blank')
                """,
                problemId
        );
        long codingTagId = findOrCreateTagId("type:coding");
        jdbcTemplate.update(
                "insert into problem_problem_tags(problem_id, problemtag_id) values (?, ?) on conflict do nothing",
                problemId,
                codingTagId
        );
    }

    private long findOrCreateTagId(String tagName) {
        Long existing = jdbcTemplate.query(
                "select id from problem_tag where name = ? order by id asc limit 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                tagName
        );
        if (existing != null) {
            return existing;
        }
        Long created = jdbcTemplate.queryForObject(
                "insert into problem_tag(name) values (?) returning id",
                Long.class,
                tagName
        );
        if (created == null) {
            throw new IllegalStateException("Failed to create tag");
        }
        return created;
    }

    private Map<String, Object> normalizeStatisticInfo(Map<String, Object> statisticInfo) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (statisticInfo != null) {
            normalized.putAll(statisticInfo);
        }
        normalized.put("question_type", "coding");
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON field", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private record PermissionContext(
            boolean canManageAllProblems,
            boolean teacher,
            Set<Long> accessibleLanguagePackIds,
            ApiResponse<Object> errorResponse
    ) {
    }

    private record OwnershipContext(
            boolean exists,
            String createdByUsername
    ) {
    }

    private record ImportTestCaseResult(
            String testCaseId,
            List<Map<String, Object>> info
    ) {
    }

    private record ImportedZipTestCases(
            List<LanguagePackProblemPackage.TestCase> testCases,
            List<Map<String, Object>> scoreFallback,
            boolean spj
    ) {
    }

    private record ImportedProblemPackage(
            LanguagePackProblemPackage problemPackage,
            List<String> tags,
            List<Map<String, Object>> testCaseScore,
            boolean spj,
            String source
    ) {
    }

    private record FpsProblem(
            String title,
            String description,
            String input,
            String output,
            String hint,
            String source,
            int timeLimitMs,
            int memoryLimitMb,
            Map<String, String> template,
            Map<String, String> prepend,
            Map<String, String> append,
            List<FpsSample> samples,
            List<FpsTestCase> testCases,
            List<FpsImage> images,
            boolean spj
    ) {
        private FpsProblem withDescription(String value) {
            return new FpsProblem(
                    title,
                    value,
                    input,
                    output,
                    hint,
                    source,
                    timeLimitMs,
                    memoryLimitMb,
                    template,
                    prepend,
                    append,
                    samples,
                    testCases,
                    images,
                    spj
            );
        }

        private FpsProblem withInput(String value) {
            return new FpsProblem(
                    title,
                    description,
                    value,
                    output,
                    hint,
                    source,
                    timeLimitMs,
                    memoryLimitMb,
                    template,
                    prepend,
                    append,
                    samples,
                    testCases,
                    images,
                    spj
            );
        }

        private FpsProblem withOutput(String value) {
            return new FpsProblem(
                    title,
                    description,
                    input,
                    value,
                    hint,
                    source,
                    timeLimitMs,
                    memoryLimitMb,
                    template,
                    prepend,
                    append,
                    samples,
                    testCases,
                    images,
                    spj
            );
        }
    }

    private record FpsSample(
            String input,
            String output
    ) {
    }

    private record FpsTestCase(
            String input,
            String output
    ) {
    }

    private record FpsImage(
            String src,
            byte[] blob
    ) {
    }

    private static class ImportProblemException extends RuntimeException {
        private ImportProblemException(String message) {
            super(message);
        }
    }
}

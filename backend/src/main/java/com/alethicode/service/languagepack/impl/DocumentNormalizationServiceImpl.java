package com.alethicode.service.languagepack.impl;

import com.alethicode.config.AlethicodeProperties;
import com.alethicode.exception.BadRequestException;
import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.DocumentNormalizationService;
import com.alethicode.service.languagepack.LanguagePackInitService;
import com.alethicode.service.languagepack.storage.LanguagePackStorageService;
import com.alethicode.service.languagepack.storage.LanguagePackStorageService.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class DocumentNormalizationServiceImpl implements DocumentNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNormalizationServiceImpl.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".pptx", ".docx", ".ppt", ".doc");
    private static final String PYTHON_BASIC_SLUG = "python-basic";
    private static final int PYTHON_BASIC_CHAPTER_COUNT = 7;

    private final JdbcTemplate jdbcTemplate;
    private final LanguagePackStorageService storageService;
    private final LanguagePackInitService initService;
    private final AlethicodeProperties.LanguagePack config;

    public DocumentNormalizationServiceImpl(JdbcTemplate jdbcTemplate,
                                            LanguagePackStorageService storageService,
                                            LanguagePackInitService initService,
                                            AlethicodeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
        this.initService = initService;
        this.config = properties.getLanguagePack();
    }

    @Override
    public void uploadAndNormalize(Long taskId, List<MultipartFile> files) {
        String currentStage = jdbcTemplate.queryForObject(
                "SELECT stage FROM language_pack_init_task WHERE id = ?",
                String.class, taskId
        );
        if (currentStage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Init task not found");
        }
        if (!"created".equals(currentStage) && !"normalizing".equals(currentStage)) {
            throw new BadRequestException("Cannot upload documents in stage: " + currentStage);
        }

        Long languagePackId = jdbcTemplate.queryForObject(
                "SELECT language_pack_id FROM language_pack_init_task WHERE id = ?",
                Long.class, taskId
        );
        String languagePackSlug = jdbcTemplate.queryForObject(
                "SELECT slug FROM language_pack WHERE id = ?",
                String.class, languagePackId
        );

        if ("created".equals(currentStage)) {
            initService.advanceStage(taskId, "normalizing");
        }

        List<DocumentUploadPlan> uploadPlans = buildUploadPlans(taskId, languagePackSlug, files);
        for (DocumentUploadPlan plan : uploadPlans) {
            processOneFile(taskId, languagePackId, plan.file(), plan.sortOrder());
        }

        int docCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_pack_document WHERE init_task_id = ? AND status = 'normalized'",
                Integer.class, taskId
        );
        jdbcTemplate.update(
                "UPDATE language_pack SET document_count = ?, update_time = now() WHERE id = ?",
                docCount, languagePackId
        );
    }

    private List<DocumentUploadPlan> buildUploadPlans(Long taskId, String languagePackSlug, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }
        if (PYTHON_BASIC_SLUG.equals(languagePackSlug)) {
            return buildPythonBasicUploadPlans(taskId, files);
        }
        return buildGeneralUploadPlans(taskId, files);
    }

    private List<DocumentUploadPlan> buildPythonBasicUploadPlans(Long taskId, List<MultipartFile> files) {
        Map<Integer, String> chapterByIndex = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT original_filename, sort_order FROM language_pack_document WHERE init_task_id = ?",
                taskId
        )) {
            String filename = stringVal(row.get("original_filename"));
            Integer sortOrder = intVal(row.get("sort_order"));
            Integer chapterIndex = sortOrder != null && sortOrder > 0
                    ? sortOrder
                    : LanguagePackChapterIndexResolver.resolveForPptFilename(filename);
            if (chapterIndex == null) {
                throw new BadRequestException("python-basic 文档无法解析章节号: " + filename);
            }
            ensureUniqueChapter(chapterByIndex, chapterIndex, filename);
        }

        List<DocumentUploadPlan> plans = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = normalizedFilename(file);
            if (!LanguagePackChapterIndexResolver.isPptFilename(filename)) {
                throw new BadRequestException("python-basic 仅支持 PPT/PPTX 文件: " + filename);
            }
            Integer chapterIndex = LanguagePackChapterIndexResolver.resolveForPptFilename(filename);
            if (chapterIndex == null) {
                throw new BadRequestException("python-basic 文件名必须包含章节号（第X章或PPTX）: " + filename);
            }
            ensureUniqueChapter(chapterByIndex, chapterIndex, filename);
            plans.add(new DocumentUploadPlan(file, chapterIndex));
        }

        if (chapterByIndex.size() != PYTHON_BASIC_CHAPTER_COUNT) {
            throw new BadRequestException("python-basic 必须且只能包含 7 章 PPT，当前识别到 " + chapterByIndex.size() + " 章");
        }
        for (int expected = 1; expected <= PYTHON_BASIC_CHAPTER_COUNT; expected++) {
            if (!chapterByIndex.containsKey(expected)) {
                throw new BadRequestException("python-basic 章节号必须完整覆盖 1..7，缺少章节: " + expected);
            }
        }
        return plans;
    }

    private List<DocumentUploadPlan> buildGeneralUploadPlans(Long taskId, List<MultipartFile> files) {
        Integer maxSortOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM language_pack_document WHERE init_task_id = ?",
                Integer.class,
                taskId
        );
        int nextSortOrder = maxSortOrder == null ? 0 : maxSortOrder;
        Set<Integer> usedSortOrders = new java.util.LinkedHashSet<>();
        for (Integer existing : jdbcTemplate.queryForList(
                "SELECT sort_order FROM language_pack_document WHERE init_task_id = ?",
                Integer.class,
                taskId
        )) {
            if (existing != null && existing > 0) {
                usedSortOrders.add(existing);
            }
        }

        List<DocumentUploadPlan> plans = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = normalizedFilename(file);
            Integer sortOrder = LanguagePackChapterIndexResolver.resolveForPptFilename(filename);
            if (sortOrder != null) {
                if (usedSortOrders.contains(sortOrder)) {
                    throw new BadRequestException("章节号重复，无法确定文档顺序: " + filename + " (章节 " + sortOrder + ")");
                }
                usedSortOrders.add(sortOrder);
                plans.add(new DocumentUploadPlan(file, sortOrder));
                continue;
            }
            nextSortOrder++;
            while (usedSortOrders.contains(nextSortOrder)) {
                nextSortOrder++;
            }
            usedSortOrders.add(nextSortOrder);
            plans.add(new DocumentUploadPlan(file, nextSortOrder));
        }
        return plans;
    }

    private void ensureUniqueChapter(Map<Integer, String> chapterByIndex, Integer chapterIndex, String filename) {
        if (chapterIndex <= 0) {
            throw new BadRequestException("章节号必须大于 0: " + filename);
        }
        String existing = chapterByIndex.putIfAbsent(chapterIndex, filename);
        if (existing != null) {
            throw new BadRequestException("章节号重复: " + chapterIndex + "，冲突文件: " + existing + " / " + filename);
        }
    }

    private String normalizedFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("File has no name");
        }
        return originalFilename.strip();
    }

    private void processOneFile(Long taskId, Long languagePackId, MultipartFile file, Integer sortOrder) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("File has no name");
        }

        String extension = extractExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file type: " + extension);
        }

        StoredFile stored;
        try {
            stored = storageService.storeOriginal(taskId, file);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to store file", e);
        }

        Long existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM language_pack_document WHERE init_task_id = ? AND file_hash = ?",
                Long.class, taskId, stored.hash()
        );
        if (existingCount != null && existingCount > 0) {
            throw new BadRequestException("Duplicate file in this task: " + originalFilename);
        }

        jdbcTemplate.update(
                """
                INSERT INTO language_pack_document(init_task_id, language_pack_id, original_filename,
                    original_path, file_hash, file_size_bytes, status, sort_order, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'normalizing', ?, now(), now())
                """,
                taskId, languagePackId, originalFilename,
                stored.path().toString(), stored.hash(), stored.sizeBytes(), sortOrder
        );

        Long docId = jdbcTemplate.queryForObject(
                "SELECT id FROM language_pack_document WHERE init_task_id = ? AND file_hash = ?",
                Long.class, taskId, stored.hash()
        );

        try {
            NormalizationResult result = normalize(taskId, stored.path(), extension);
            jdbcTemplate.update(
                    """
                    UPDATE language_pack_document
                    SET canonical_path = ?, preview_pdf_path = ?, status = 'normalized', update_time = now()
                    WHERE id = ?
                    """,
                    result.canonicalPath().toString(),
                    result.previewPdfPath().toString(),
                    docId
            );
        } catch (Exception e) {
            log.error("Normalization failed for document {}: {}", originalFilename, e.getMessage());
            jdbcTemplate.update(
                    "UPDATE language_pack_document SET status = 'failed', failure_reason = ?, update_time = now() WHERE id = ?",
                    e.getMessage(), docId
            );
            failTask(taskId, "Document normalization failed: " + originalFilename + " — " + e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Document normalization failed", e);
        }
    }

    private NormalizationResult normalize(Long taskId, Path originalPath, String extension) throws Exception {
        Path canonicalPath;
        Path previewPdfPath;

        if (".pdf".equals(extension)) {
            canonicalPath = storageService.storeCanonical(taskId, originalPath, originalPath.getFileName().toString());
            previewPdfPath = storageService.storePreview(taskId, originalPath, originalPath.getFileName().toString());
        } else if (".pptx".equals(extension)) {
            canonicalPath = storageService.storeCanonical(taskId, originalPath, originalPath.getFileName().toString());
            previewPdfPath = convertToPreviewPdf(taskId, originalPath);
        } else if (".docx".equals(extension)) {
            canonicalPath = storageService.storeCanonical(taskId, originalPath, originalPath.getFileName().toString());
            previewPdfPath = convertToPreviewPdf(taskId, originalPath);
        } else if (".ppt".equals(extension)) {
            Path converted = convertLegacyFormat(originalPath, extension);
            canonicalPath = storageService.storeCanonical(taskId, converted, replaceExtension(originalPath.getFileName().toString(), extension));
            previewPdfPath = convertToPreviewPdf(taskId, converted);
        } else if (".doc".equals(extension)) {
            Path converted = convertLegacyFormat(originalPath, extension);
            canonicalPath = storageService.storeCanonical(taskId, converted, replaceExtension(originalPath.getFileName().toString(), extension));
            previewPdfPath = convertToPreviewPdf(taskId, converted);
        } else {
            throw new BadRequestException("Unsupported extension: " + extension);
        }

        return new NormalizationResult(canonicalPath, previewPdfPath);
    }

    private Path convertLegacyFormat(Path inputPath, String extension) throws Exception {
        String targetExtension = ".ppt".equals(extension) ? "pptx" : "docx";
        return runLibreOfficeConvert(inputPath, targetExtension);
    }

    private Path convertToPreviewPdf(Long taskId, Path inputPath) throws Exception {
        Path pdfOutput = runLibreOfficeConvert(inputPath, "pdf");
        return storageService.storePreview(taskId, pdfOutput, replaceExtensionToPdf(inputPath.getFileName().toString()));
    }

    private Path runLibreOfficeConvert(Path inputPath, String targetFormat) throws Exception {
        Path outputDir = inputPath.getParent();
        ProcessBuilder pb = new ProcessBuilder(
                config.getLibreOfficePath(),
                "--headless",
                "--convert-to", targetFormat,
                "--outdir", outputDir.toString(),
                inputPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("LibreOffice conversion failed (exit " + exitCode + "): " + output);
        }

        String baseName = inputPath.getFileName().toString();
        int dotIdx = baseName.lastIndexOf('.');
        String newName = (dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName) + "." + targetFormat;
        Path result = outputDir.resolve(newName);
        if (!result.toFile().exists()) {
            throw new RuntimeException("LibreOffice output file not found: " + result);
        }
        return result;
    }

    private void failTask(Long taskId, String reason) {
        jdbcTemplate.update(
                "UPDATE language_pack_init_task SET stage = 'failed', failure_reason = ?, update_time = now() WHERE id = ?",
                reason, taskId
        );
        jdbcTemplate.update(
                """
                INSERT INTO language_pack_init_stage_log(task_id, from_stage, to_stage, message, create_time)
                VALUES (?, 'normalizing', 'failed', ?, now())
                """,
                taskId, reason
        );
    }

    private String extractExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0) {
            return "";
        }
        return filename.substring(dotIdx).toLowerCase();
    }

    private String stringVal(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).strip();
    }

    private Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String replaceExtension(String filename, String oldExtension) {
        String newExt = ".ppt".equals(oldExtension) ? ".pptx" : ".docx";
        int dotIdx = filename.lastIndexOf('.');
        return (dotIdx > 0 ? filename.substring(0, dotIdx) : filename) + newExt;
    }

    private String replaceExtensionToPdf(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        return (dotIdx > 0 ? filename.substring(0, dotIdx) : filename) + ".pdf";
    }

    private record NormalizationResult(Path canonicalPath, Path previewPdfPath) {
    }

    private record DocumentUploadPlan(MultipartFile file, Integer sortOrder) {
    }
}

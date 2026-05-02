package com.alethicode.service.languagepack.impl;

import com.alethicode.exception.BusinessException;
import com.alethicode.exception.ErrorCode;
import com.alethicode.service.languagepack.LanguagePackDocumentQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class LanguagePackDocumentQueryServiceImpl implements LanguagePackDocumentQueryService {

    private final JdbcTemplate jdbcTemplate;

    public LanguagePackDocumentQueryServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> listDocuments(Long taskId) {
        return jdbcTemplate.query(
                """
                SELECT id, init_task_id, language_pack_id, original_filename, canonical_path,
                       preview_pdf_path, file_hash, file_size_bytes, page_count, status,
                       failure_reason, sort_order, create_time, update_time
                FROM language_pack_document
                WHERE init_task_id = ?
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("init_task_id", rs.getLong("init_task_id"));
                    row.put("language_pack_id", rs.getLong("language_pack_id"));
                    row.put("original_filename", rs.getString("original_filename"));
                    row.put("canonical_path", rs.getString("canonical_path"));
                    row.put("preview_pdf_path", rs.getString("preview_pdf_path"));
                    row.put("file_hash", rs.getString("file_hash"));
                    row.put("file_size_bytes", rs.getLong("file_size_bytes"));
                    row.put("page_count", rs.getInt("page_count"));
                    row.put("status", rs.getString("status"));
                    row.put("failure_reason", rs.getString("failure_reason"));
                    row.put("sort_order", rs.getInt("sort_order"));
                    row.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    row.put("update_time", toInstant(rs.getTimestamp("update_time")));
                    return row;
                },
                taskId
        );
    }

    @Override
    public List<Map<String, Object>> listPages(Long documentId) {
        return jdbcTemplate.query(
                """
                SELECT id, document_id, language_pack_id, page_no, chunk_index,
                       page_title, excerpt, preview_asset_path, text_hash, create_time
                FROM language_pack_page
                WHERE document_id = ?
                ORDER BY page_no, chunk_index
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("document_id", rs.getLong("document_id"));
                    row.put("language_pack_id", rs.getLong("language_pack_id"));
                    row.put("page_no", rs.getInt("page_no"));
                    row.put("chunk_index", rs.getInt("chunk_index"));
                    row.put("page_title", rs.getString("page_title"));
                    row.put("excerpt", rs.getString("excerpt"));
                    row.put("preview_asset_path", rs.getString("preview_asset_path"));
                    row.put("text_hash", rs.getString("text_hash"));
                    row.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    return row;
                },
                documentId
        );
    }

    @Override
    public Map<String, Object> getPage(Long languagePackId, Long documentId, Integer pageNo) {
        List<Map<String, Object>> results = jdbcTemplate.query(
                """
                SELECT id, document_id, language_pack_id, page_no, chunk_index,
                       page_title, page_text, excerpt, preview_asset_path, text_hash, create_time
                FROM language_pack_page
                WHERE language_pack_id = ? AND document_id = ? AND page_no = ?
                ORDER BY chunk_index
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("document_id", rs.getLong("document_id"));
                    row.put("language_pack_id", rs.getLong("language_pack_id"));
                    row.put("page_no", rs.getInt("page_no"));
                    row.put("chunk_index", rs.getInt("chunk_index"));
                    row.put("page_title", rs.getString("page_title"));
                    row.put("page_text", rs.getString("page_text"));
                    row.put("excerpt", rs.getString("excerpt"));
                    row.put("preview_asset_path", rs.getString("preview_asset_path"));
                    row.put("text_hash", rs.getString("text_hash"));
                    row.put("create_time", toInstant(rs.getTimestamp("create_time")));
                    return row;
                },
                languagePackId, documentId, pageNo
        );
        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Page not found");
        }
        return results.getFirst();
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

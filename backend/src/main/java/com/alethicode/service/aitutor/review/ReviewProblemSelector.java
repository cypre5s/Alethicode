package com.alethicode.service.aitutor.review;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 选题策略：在 review-package 创建路径上挑出 N 道相关错题（Phase 3 抽离）。
 * 优先级：supplement plan 推荐的题 > 错题本未掌握题 > submission 历史的 WA 题。
 */
@Component
class ReviewProblemSelector {

    static final int REVIEW_PROBLEM_COUNT = 3;

    private final JdbcTemplate jdbcTemplate;

    ReviewProblemSelector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Long> select(Long userId, String errorTaxonomy, Long languagePackId, List<Long> prioritizedProblemIds) {
        List<Long> result = new ArrayList<>();
        if (prioritizedProblemIds != null) {
            for (Long problemId : prioritizedProblemIds) {
                if (problemId != null && !result.contains(problemId)) result.add(problemId);
            }
        }
        if (result.size() >= REVIEW_PROBLEM_COUNT) return result.subList(0, REVIEW_PROBLEM_COUNT);

        String notebookSql = """
                select distinct n.problem_id
                from ai_learner_notebook n
                join language_pack_problem_mapping lpm on lpm.problem_id = n.problem_id
                where n.user_id = ? and n.error_taxonomy = ? and n.is_deleted = false
                  and n.problem_id is not null
                  and (? is null or lpm.language_pack_id = ?)
                  and not exists (
                      select 1 from ai_error_review_problem rp
                      join ai_error_review_package pkg on pkg.id = rp.package_id
                      where rp.problem_id = n.problem_id and pkg.user_id = ? and pkg.error_taxonomy = ?
                        and rp.submitted = true and rp.is_correct = true
                  )
                order by n.problem_id limit ?
                """;
        List<Long> fromNotebook = jdbcTemplate.query(notebookSql,
                (rs, rowNum) -> rs.getLong("problem_id"),
                userId, errorTaxonomy, languagePackId, languagePackId, userId, errorTaxonomy, REVIEW_PROBLEM_COUNT);
        for (Long problemId : fromNotebook) {
            if (!result.contains(problemId)) result.add(problemId);
            if (result.size() >= REVIEW_PROBLEM_COUNT) return result.subList(0, REVIEW_PROBLEM_COUNT);
        }

        int remaining = REVIEW_PROBLEM_COUNT - result.size();
        StringBuilder fallbackSql = new StringBuilder("""
                select s.problem_id from submission s
                join language_pack_problem_mapping lpm on lpm.problem_id = s.problem_id
                where s.user_id = ? and s.result <> 0
                """);
        List<Object> fallbackArgs = new ArrayList<>();
        fallbackArgs.add(userId);
        if (languagePackId != null) {
            fallbackSql.append("\n  and lpm.language_pack_id = ?");
            fallbackArgs.add(languagePackId);
        }
        if (!result.isEmpty()) {
            fallbackSql.append("\n  and s.problem_id not in (");
            for (int i = 0; i < result.size(); i++) {
                if (i > 0) fallbackSql.append(", ");
                fallbackSql.append("?");
                fallbackArgs.add(result.get(i));
            }
            fallbackSql.append(")");
        }
        fallbackSql.append("\n group by s.problem_id\n order by max(s.create_time) desc\n limit ?");
        fallbackArgs.add(remaining);

        List<Long> fromSubmissions = jdbcTemplate.query(fallbackSql.toString(),
                (rs, rowNum) -> rs.getLong("problem_id"),
                fallbackArgs.toArray());
        for (Long problemId : fromSubmissions) {
            if (!result.contains(problemId)) result.add(problemId);
        }
        return result.size() > REVIEW_PROBLEM_COUNT ? result.subList(0, REVIEW_PROBLEM_COUNT) : result;
    }
}

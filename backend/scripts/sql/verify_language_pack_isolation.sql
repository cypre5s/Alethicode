-- Read-only verification script for V29 strong isolation.
-- Usage:
--   psql "$DATABASE_URL" -f backend/scripts/sql/verify_language_pack_isolation.sql

\echo '== 1) problem must map to exactly one language pack =='
SELECT COUNT(*) AS duplicated_problem_mappings
FROM (
    SELECT problem_id
    FROM language_pack_problem_mapping
    GROUP BY problem_id
    HAVING COUNT(*) > 1
) t;

\echo '== 2) ai_problem_kc_mapping must align with problem->pack mapping =='
SELECT COUNT(*) AS mapping_pack_mismatch
FROM ai_problem_kc_mapping m
JOIN language_pack_problem_mapping lpm ON lpm.problem_id = m.problem_id
WHERE m.language_pack_id IS DISTINCT FROM lpm.language_pack_id;

\echo '== 3) no NULL language_pack_id for language-pack problems =='
SELECT COUNT(*) AS null_pack_in_problem_kc_mapping
FROM ai_problem_kc_mapping m
JOIN language_pack_problem_mapping lpm ON lpm.problem_id = m.problem_id
WHERE m.language_pack_id IS NULL;

\echo '== 4) ai_knowledge_component uniqueness in each pack by normalized name =='
SELECT COUNT(*) AS duplicated_kc_per_pack
FROM (
    SELECT language_pack_id, name_normalized
    FROM ai_knowledge_component
    WHERE language_pack_id IS NOT NULL
      AND name_normalized <> ''
    GROUP BY language_pack_id, name_normalized
    HAVING COUNT(*) > 1
) t;

\echo '== 5) classroom_language_pack bindings summary =='
SELECT
    COUNT(*) AS binding_count,
    COUNT(DISTINCT classroom_id) AS classroom_count,
    COUNT(DISTINCT language_pack_id) AS language_pack_count
FROM classroom_language_pack;

\echo '== 6) top mismatch samples (should return 0 rows) =='
SELECT
    m.problem_id,
    m.kc_id,
    m.language_pack_id AS mapping_pack_id,
    lpm.language_pack_id AS problem_pack_id
FROM ai_problem_kc_mapping m
JOIN language_pack_problem_mapping lpm ON lpm.problem_id = m.problem_id
WHERE m.language_pack_id IS DISTINCT FROM lpm.language_pack_id
ORDER BY m.problem_id, m.kc_id
LIMIT 20;

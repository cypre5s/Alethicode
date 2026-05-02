-- V29: strengthen language-pack isolation boundaries and backfill legacy data

-- ===== 1) schema extensions =====

ALTER TABLE ai_knowledge_component
    ADD COLUMN IF NOT EXISTS language_pack_id BIGINT REFERENCES language_pack(id) ON DELETE SET NULL;

ALTER TABLE ai_knowledge_component
    ADD COLUMN IF NOT EXISTS name_normalized VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE ai_problem_kc_mapping
    ADD COLUMN IF NOT EXISTS language_pack_id BIGINT REFERENCES language_pack(id) ON DELETE SET NULL;

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS teaching_explanation TEXT NOT NULL DEFAULT '';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS common_mistakes_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS source_pages_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE language_pack_problem_generation_log
    ADD COLUMN IF NOT EXISTS related_kc_ids_json TEXT NOT NULL DEFAULT '[]';

CREATE TABLE IF NOT EXISTS classroom_language_pack (
    id                BIGSERIAL PRIMARY KEY,
    classroom_id      VARCHAR(64) NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    language_pack_id  BIGINT      NOT NULL REFERENCES language_pack(id) ON DELETE CASCADE,
    create_time       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (classroom_id, language_pack_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_language_pack_classroom
    ON classroom_language_pack(classroom_id);

CREATE INDEX IF NOT EXISTS idx_classroom_language_pack_pack
    ON classroom_language_pack(language_pack_id);

CREATE INDEX IF NOT EXISTS idx_ai_problem_kc_mapping_pack
    ON ai_problem_kc_mapping(language_pack_id);

CREATE INDEX IF NOT EXISTS idx_ai_problem_kc_mapping_problem_pack
    ON ai_problem_kc_mapping(problem_id, language_pack_id);

CREATE INDEX IF NOT EXISTS idx_ai_kc_language_pack
    ON ai_knowledge_component(language_pack_id);

-- ===== 2) normalize and backfill legacy data =====

DO $$
DECLARE
    default_pack_id BIGINT;
    python_basic_next_version INTEGER;
BEGIN
    -- 2.1 resolve default pack, create "Python基础" if none exists
    SELECT id
    INTO default_pack_id
    FROM language_pack
    WHERE primary_language = 'Python3'
      AND (slug = 'python-basic' OR name = 'Python基础')
    ORDER BY
        CASE WHEN status = 'published' THEN 0 ELSE 1 END,
        version DESC,
        update_time DESC,
        id DESC
    LIMIT 1;

    IF default_pack_id IS NULL THEN
        SELECT COALESCE(MAX(version), 0) + 1
        INTO python_basic_next_version
        FROM language_pack
        WHERE slug = 'python-basic';

        INSERT INTO language_pack(
            slug, version, name, primary_language, description, status, create_time, update_time
        ) VALUES (
            'python-basic',
            python_basic_next_version,
            'Python基础',
            'Python3',
            'Legacy default language pack for historical data migration.',
            'published',
            now(),
            now()
        )
        RETURNING id INTO default_pack_id;
    END IF;

    -- 2.2 fill name_normalized for historical KCs
    UPDATE ai_knowledge_component
    SET name_normalized = COALESCE(
        NULLIF(name_normalized, ''),
        NULLIF(regexp_replace(lower(trim(COALESCE(name, ''))), '[[:space:]-]+', '_', 'g'), ''),
        CONCAT('kc_', id::text)
    );

    -- 2.3 attach all historical KCs to default pack first
    UPDATE ai_knowledge_component
    SET language_pack_id = default_pack_id
    WHERE language_pack_id IS NULL;

    -- 2.4 ensure language-pack mapping exists for historical AI-KC problems
    INSERT INTO language_pack_problem_mapping(language_pack_id, problem_id, generation_log_id, create_time)
    SELECT default_pack_id, src.problem_id, NULL, now()
    FROM (
        SELECT DISTINCT problem_id
        FROM ai_problem_kc_mapping
    ) src
    WHERE NOT EXISTS (
        SELECT 1
        FROM language_pack_problem_mapping lpm
        WHERE lpm.problem_id = src.problem_id
    );

    -- 2.5 keep one mapping row per problem first, prefer default-pack row
    DELETE FROM language_pack_problem_mapping doomed
    USING (
        SELECT id
        FROM (
            SELECT id,
                   ROW_NUMBER() OVER (
                       PARTITION BY problem_id
                       ORDER BY
                           CASE WHEN language_pack_id = default_pack_id THEN 0 ELSE 1 END,
                           create_time DESC,
                           id DESC
                   ) AS rn
            FROM language_pack_problem_mapping
        ) ranked
        WHERE rn > 1
    ) victims
    WHERE doomed.id = victims.id;

    -- 2.6 normalize all current problem mappings to default pack ("Python基础")
    UPDATE language_pack_problem_mapping
    SET language_pack_id = default_pack_id
    WHERE language_pack_id <> default_pack_id;

    -- 2.7 backfill ai_problem_kc_mapping.language_pack_id from normalized problem mapping
    UPDATE ai_problem_kc_mapping m
    SET language_pack_id = lpm.language_pack_id
    FROM language_pack_problem_mapping lpm
    WHERE lpm.problem_id = m.problem_id
      AND (m.language_pack_id IS NULL OR m.language_pack_id <> lpm.language_pack_id);

    -- 2.8 ensure each classroom has default pack binding for historical behavior continuity
    INSERT INTO classroom_language_pack(classroom_id, language_pack_id, create_time)
    SELECT c.id, default_pack_id, now()
    FROM classroom c
    ON CONFLICT (classroom_id, language_pack_id) DO NOTHING;
END $$;

-- 2.9 enforce one problem belongs to at most one language pack
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_problem_mapping_problem
    ON language_pack_problem_mapping(problem_id);

-- 2.10 build pack-local KC copies for language-pack problems and remap ai_problem_kc_mapping
DO $$
BEGIN
    INSERT INTO ai_knowledge_component(
        name, name_en, chapter, description, p_init, p_transit, p_slip, p_guess, language_pack_id, name_normalized
    )
    SELECT DISTINCT
        old_kc.name,
        old_kc.name_en,
        old_kc.chapter,
        old_kc.description,
        old_kc.p_init,
        old_kc.p_transit,
        old_kc.p_slip,
        old_kc.p_guess,
        lpm.language_pack_id,
        COALESCE(
            NULLIF(old_kc.name_normalized, ''),
            NULLIF(regexp_replace(lower(trim(COALESCE(old_kc.name, ''))), '[[:space:]-]+', '_', 'g'), ''),
            CONCAT('kc_', old_kc.id::text)
        ) AS normalized_name
    FROM ai_problem_kc_mapping m
    JOIN language_pack_problem_mapping lpm ON lpm.problem_id = m.problem_id
    JOIN ai_knowledge_component old_kc ON old_kc.id = m.kc_id
    ON CONFLICT DO NOTHING;

    INSERT INTO ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id)
    SELECT
        m.problem_id,
        new_kc.id AS kc_id,
        m.weight,
        lpm.language_pack_id
    FROM ai_problem_kc_mapping m
    JOIN language_pack_problem_mapping lpm ON lpm.problem_id = m.problem_id
    JOIN ai_knowledge_component old_kc ON old_kc.id = m.kc_id
    JOIN ai_knowledge_component new_kc
      ON new_kc.language_pack_id = lpm.language_pack_id
     AND new_kc.name_normalized = COALESCE(
         NULLIF(old_kc.name_normalized, ''),
         NULLIF(regexp_replace(lower(trim(COALESCE(old_kc.name, ''))), '[[:space:]-]+', '_', 'g'), ''),
         CONCAT('kc_', old_kc.id::text)
     )
    ON CONFLICT (problem_id, kc_id)
    DO UPDATE SET
        weight = GREATEST(ai_problem_kc_mapping.weight, EXCLUDED.weight),
        language_pack_id = EXCLUDED.language_pack_id;

    DELETE FROM ai_problem_kc_mapping old_mapping
    USING language_pack_problem_mapping lpm,
          ai_knowledge_component old_kc,
          ai_knowledge_component new_kc
    WHERE old_mapping.problem_id = lpm.problem_id
      AND old_mapping.kc_id = old_kc.id
      AND new_kc.language_pack_id = lpm.language_pack_id
      AND new_kc.name_normalized = COALESCE(
          NULLIF(old_kc.name_normalized, ''),
          NULLIF(regexp_replace(lower(trim(COALESCE(old_kc.name, ''))), '[[:space:]-]+', '_', 'g'), ''),
          CONCAT('kc_', old_kc.id::text)
      )
      AND old_mapping.kc_id <> new_kc.id;
END $$;

-- 2.11 enforce pack-local KC uniqueness
DO $$
BEGIN
    CREATE TEMP TABLE tmp_ai_kc_merge (
        old_kc_id BIGINT PRIMARY KEY,
        keep_kc_id BIGINT NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO tmp_ai_kc_merge(old_kc_id, keep_kc_id)
    SELECT old_id, keep_id
    FROM (
        SELECT
            id AS old_id,
            FIRST_VALUE(id) OVER (
                PARTITION BY language_pack_id, name_normalized
                ORDER BY id ASC
            ) AS keep_id
        FROM ai_knowledge_component
        WHERE language_pack_id IS NOT NULL
          AND name_normalized <> ''
    ) ranked
    WHERE old_id <> keep_id;

    INSERT INTO ai_problem_kc_mapping(problem_id, kc_id, weight, language_pack_id)
    SELECT
        m.problem_id,
        merge.keep_kc_id,
        m.weight,
        m.language_pack_id
    FROM ai_problem_kc_mapping m
    JOIN tmp_ai_kc_merge merge ON merge.old_kc_id = m.kc_id
    ON CONFLICT (problem_id, kc_id)
    DO UPDATE SET
        weight = GREATEST(ai_problem_kc_mapping.weight, EXCLUDED.weight),
        language_pack_id = COALESCE(ai_problem_kc_mapping.language_pack_id, EXCLUDED.language_pack_id);

    UPDATE ai_misconception mc
    SET kc_id = merge.keep_kc_id
    FROM tmp_ai_kc_merge merge
    WHERE mc.kc_id = merge.old_kc_id;

    DELETE FROM ai_problem_kc_mapping m
    USING tmp_ai_kc_merge merge
    WHERE m.kc_id = merge.old_kc_id;

    DELETE FROM ai_knowledge_component kc
    USING tmp_ai_kc_merge merge
    WHERE kc.id = merge.old_kc_id;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_kc_pack_normalized
    ON ai_knowledge_component(language_pack_id, name_normalized)
    WHERE language_pack_id IS NOT NULL;

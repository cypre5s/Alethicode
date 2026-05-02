-- V30: enforce one language pack binding per classroom

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM classroom_language_pack
        GROUP BY classroom_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'classroom_language_pack contains classrooms bound to multiple language packs; clean historical data before applying V30';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_classroom_language_pack_classroom
    ON classroom_language_pack(classroom_id);

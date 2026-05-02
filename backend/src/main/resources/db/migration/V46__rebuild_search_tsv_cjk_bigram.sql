-- V46: Rebuild search_tsv with CJK bigram tokenization
-- The 'simple' text search config cannot tokenize Chinese text.
-- This migration introduces a PL/pgSQL function that splits CJK character
-- sequences into overlapping 2-character bigrams while keeping non-CJK
-- alphanumeric tokens as-is, then rebuilds all search_tsv values.

CREATE OR REPLACE FUNCTION cjk_bigram_tokenize(input_text text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT
AS $$
DECLARE
    result     text := '';
    cjk_buf    text := '';
    ch         text;
    code_point int;
    is_cjk     boolean;
    i          int;
    buf_len    int;
    j          int;
BEGIN
    IF input_text IS NULL OR length(input_text) = 0 THEN
        RETURN '';
    END IF;

    FOR i IN 1..length(input_text) LOOP
        ch := substr(input_text, i, 1);
        code_point := ascii(ch);

        is_cjk := (code_point >= 19968 AND code_point <= 40959)   -- CJK Unified Ideographs
                OR (code_point >= 13312 AND code_point <= 19903)   -- CJK Extension A
                OR (code_point >= 131072 AND code_point <= 173791); -- CJK Extension B+

        IF is_cjk THEN
            cjk_buf := cjk_buf || ch;
        ELSE
            IF length(cjk_buf) > 0 THEN
                buf_len := length(cjk_buf);
                IF buf_len = 1 THEN
                    result := result || ' ' || cjk_buf;
                ELSE
                    FOR j IN 1..(buf_len - 1) LOOP
                        result := result || ' ' || substr(cjk_buf, j, 2);
                    END LOOP;
                END IF;
                cjk_buf := '';
            END IF;

            IF ch ~ '[A-Za-z0-9]' THEN
                result := result || ch;
            ELSE
                result := result || ' ';
            END IF;
        END IF;
    END LOOP;

    IF length(cjk_buf) > 0 THEN
        buf_len := length(cjk_buf);
        IF buf_len = 1 THEN
            result := result || ' ' || cjk_buf;
        ELSE
            FOR j IN 1..(buf_len - 1) LOOP
                result := result || ' ' || substr(cjk_buf, j, 2);
            END LOOP;
        END IF;
    END IF;

    RETURN lower(trim(regexp_replace(result, '\s+', ' ', 'g')));
END;
$$;

UPDATE language_pack_page
SET search_tsv = to_tsvector('simple', cjk_bigram_tokenize(page_text));

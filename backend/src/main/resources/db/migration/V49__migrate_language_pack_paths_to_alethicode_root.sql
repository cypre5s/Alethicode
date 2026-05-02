UPDATE language_pack_document
SET original_path = CASE
                        WHEN original_path LIKE '/home/cypress/code_java/%'
                            THEN replace(original_path, '/home/cypress/code_java/', '/home/cypress/Alethicode/')
                        ELSE original_path
    END,
    canonical_path = CASE
                         WHEN canonical_path LIKE '/home/cypress/code_java/%'
                             THEN replace(canonical_path, '/home/cypress/code_java/', '/home/cypress/Alethicode/')
                         ELSE canonical_path
        END,
    preview_pdf_path = CASE
                           WHEN preview_pdf_path LIKE '/home/cypress/code_java/%'
                               THEN replace(preview_pdf_path, '/home/cypress/code_java/', '/home/cypress/Alethicode/')
                           ELSE preview_pdf_path
        END,
    update_time = now()
WHERE original_path LIKE '/home/cypress/code_java/%'
   OR canonical_path LIKE '/home/cypress/code_java/%'
   OR preview_pdf_path LIKE '/home/cypress/code_java/%';

UPDATE language_pack_page
SET preview_asset_path = replace(preview_asset_path, '/home/cypress/code_java/', '/home/cypress/Alethicode/')
WHERE preview_asset_path LIKE '/home/cypress/code_java/%';

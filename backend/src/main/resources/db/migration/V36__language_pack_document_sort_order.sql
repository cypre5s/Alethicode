-- V36: add sort_order to language_pack_document for admin drag-and-drop ordering

ALTER TABLE language_pack_document
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE language_pack_document SET sort_order = id WHERE sort_order = 0;

-- V94: 统一网站缩写为 Alethicode
-- 目的：
--   1. 保持 V2__init_data.sql 历史 checksum 不变，避免已部署环境 Flyway 校验失败；
--   2. 通过新增迁移把现有 sys_options.website_config.website_name_shortcut 更新为 Alethicode。
-- 风险：
--   本迁移会修改 sys_options 中 website_config 的一个 JSON 字段；不涉及 schema 变更，可重复执行。

UPDATE sys_options
SET value = jsonb_set(value, '{website_name_shortcut}', to_jsonb('Alethicode'::text), true)
WHERE key = 'website_config'
  AND COALESCE(value ->> 'website_name_shortcut', '') <> 'Alethicode';

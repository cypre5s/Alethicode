#!/usr/bin/env bash
set -euo pipefail

# 在 ECS 上跑：清残留 + 创建 root 超级管理员 + 导入业务 csv + SETVAL + UPDATE creator_id
# 参数: $1 = bcrypt hash (60 字符, 形如 $2y$10$...)
# dump 文件位置: /tmp/courseware_migration/

BCRYPT_HASH="${1:?usage: ecs_import.sh <bcrypt-hash>}"
DUMP_DIR=/tmp/courseware_migration
LP_ID=43

PG="docker exec -i java-oj-postgres psql -U onlinejudge -d alethicode"

echo "[1/5] 清 ECS 残留种子 + 清 LP ${LP_ID} 旧导入残留 + 创建/更新 root 超级管理员"
$PG -v ON_ERROR_STOP=1 <<SQL
BEGIN;
-- 清前次部分导入残留 (重跑场景下); ECS 全空时这些 DELETE 都是 0 行
DELETE FROM ai_problem_kc_mapping
  WHERE problem_id IN (SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id=${LP_ID})
     OR kc_id IN (SELECT id FROM ai_knowledge_component WHERE language_pack_id=${LP_ID});
DELETE FROM ai_knowledge_component WHERE language_pack_id=${LP_ID};
DELETE FROM problem_problem_tags
  WHERE problem_id IN (SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id=${LP_ID});
DELETE FROM problem
  WHERE id IN (SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id=${LP_ID});
-- ECS 初次部署: problem_tag 表无其他业务来源, 直接全表清, 避免重跑时 PK 冲突
DELETE FROM problem_tag;
-- language_pack 级联清掉所有子表行 (init_task -> stage_log/artifact/agent_run/batch_run/quality_report,
-- document -> page, chapter -> kc -> kc_page_mapping/kc_prerequisite, example -> example_kc_mapping,
-- problem_generation_log, review_task, problem_mapping)
DELETE FROM language_pack WHERE id=${LP_ID};
-- ECS 初始种子残留
DELETE FROM language_pack WHERE id=1 AND creator_id IS NULL AND page_count=0;

DO \$\$
DECLARE uid BIGINT;
BEGIN
  SELECT id INTO uid FROM "user" WHERE lower(username)='root';
  IF uid IS NULL THEN
    INSERT INTO "user"(username,email,password_hash,admin_type,problem_permission,is_disabled,create_time)
    VALUES ('root','root@admin.local','${BCRYPT_HASH}','Admin','All',false,now())
    RETURNING id INTO uid;
    RAISE NOTICE 'Created root with id=%', uid;
  ELSE
    UPDATE "user" SET password_hash='${BCRYPT_HASH}', admin_type='Admin', problem_permission='All', is_disabled=false WHERE id=uid;
    RAISE NOTICE 'Updated existing root with id=%', uid;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM user_profile WHERE user_id=uid) THEN
    INSERT INTO user_profile(user_id,acm_problems_status,oi_problems_status,role)
    VALUES (uid,'{}','{}','Admin(Super)');
  END IF;
END \$\$;
COMMIT;
SQL

echo "[2/5] 业务 CSV 顺序导入 (按外键拓扑)"
import_csv() {
  local table=$1
  local csv="${DUMP_DIR}/${table}.csv"
  if [[ ! -f "${csv}" ]]; then
    echo "  [skip] ${table}: csv 不存在"
    return
  fi
  local rows
  rows=$(($(wc -l < "${csv}") - 1))
  if (( rows <= 0 )); then
    echo "  [skip] ${table}: 0 数据行"
    return
  fi
  cat "${csv}" | $PG -v ON_ERROR_STOP=1 -c "COPY ${table} FROM STDIN WITH (FORMAT CSV, HEADER true)" \
    || { echo "FAILED on ${table}"; exit 1; }
  echo "  [ok]   ${table}"
}

# 依赖拓扑顺序: document/page 必须在 init_batch_run/kc_page_mapping 前
import_csv language_pack
import_csv language_pack_init_task
import_csv language_pack_init_stage_log
import_csv language_pack_init_artifact
import_csv language_pack_init_agent_run
import_csv language_pack_document
import_csv language_pack_page
import_csv language_pack_chapter
import_csv language_pack_kc
import_csv language_pack_kc_page_mapping
import_csv language_pack_kc_prerequisite
import_csv language_pack_init_batch_run
import_csv language_pack_init_quality_report
import_csv language_pack_example
import_csv language_pack_example_kc_mapping
import_csv language_pack_problem_generation_log
import_csv language_pack_review_task
import_csv problem_tag
# problem.csv 中 created_by_id=15 (本地 lbx) 在 ECS 上无对应 user; 预处理置空, 导入后再 UPDATE 指向 root
python3 - <<'PY'
import csv
p = "/tmp/courseware_migration/problem.csv"
with open(p, newline='') as f:
    rows = list(csv.DictReader(f))
if rows:
    fields = list(rows[0].keys())
    for r in rows:
        r['created_by_id'] = ''
    with open(p, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)
    print(f"[pre] problem.csv: {len(rows)} rows created_by_id 置空")
PY
import_csv problem
import_csv problem_problem_tags
import_csv language_pack_problem_mapping
import_csv ai_knowledge_component
import_csv ai_problem_kc_mapping

echo "[3/5] 修正 sequence (避免后续 INSERT 主键冲突)"
$PG -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE
  rec RECORD;
  seq_name TEXT;
  max_id BIGINT;
BEGIN
  FOR rec IN
    SELECT unnest(ARRAY[
      'language_pack',
      'language_pack_init_task',
      'language_pack_init_stage_log',
      'language_pack_init_artifact',
      'language_pack_init_agent_run',
      'language_pack_init_batch_run',
      'language_pack_init_quality_report',
      'language_pack_document',
      'language_pack_page',
      'language_pack_chapter',
      'language_pack_kc',
      'language_pack_kc_page_mapping',
      'language_pack_kc_prerequisite',
      'language_pack_example',
      'language_pack_example_kc_mapping',
      'language_pack_problem_generation_log',
      'language_pack_review_task',
      'language_pack_problem_mapping',
      'problem_tag',
      'problem',
      'problem_problem_tags',
      'ai_knowledge_component',
      'ai_problem_kc_mapping',
      '"user"',
      'user_profile'
    ]) AS table_name
  LOOP
    seq_name := pg_get_serial_sequence(rec.table_name, 'id');
    IF seq_name IS NOT NULL THEN
      EXECUTE format('SELECT GREATEST(1, COALESCE(MAX(id),0)) FROM %s', rec.table_name) INTO max_id;
      PERFORM setval(seq_name, max_id);
      RAISE NOTICE 'setval(%, %)', seq_name, max_id;
    END IF;
  END LOOP;
END $$;
SQL

echo "[4/5] 把 language_pack.creator_id + problem.created_by_id 指向 root"
$PG -v ON_ERROR_STOP=1 <<SQL
UPDATE language_pack
SET creator_id = (SELECT id FROM "user" WHERE username='root')
WHERE id = ${LP_ID};
UPDATE problem
SET created_by_id = (SELECT id FROM "user" WHERE username='root')
WHERE id IN (SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id=${LP_ID});
SELECT id, slug, name, status, document_count, page_count, problem_count, creator_id FROM language_pack;
SELECT count(*) AS problems_assigned_to_root FROM problem WHERE created_by_id=(SELECT id FROM "user" WHERE username='root');
SELECT u.username AS author FROM "user" u JOIN language_pack lp ON lp.creator_id=u.id WHERE lp.id=${LP_ID};
SQL

echo "[5/5] 业务表导入完成。下一步: import_lightrag.sh + import_memgraph.sh"

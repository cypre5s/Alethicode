#!/usr/bin/env bash
set -euo pipefail

# 把本地 PostgreSQL 中 LP_ID=43 (Python 课程包，lbx 创建) 关联的全链业务行
# 导出到 dump/<table>.csv，并把 document/page 表的本地绝对路径替换为 ECS 容器内
# 路径 (/home/cypress/Alethicode/deploy/data/language_pack/ -> /data/language_pack/).
# language_pack.creator_id 在 dump 阶段统一置 NULL，由 ECS 端在 root 用户创建后
# 单独 UPDATE 指向 root.id，避免 BIGINT REFERENCES "user"(id) 的非延迟外键检查失败.

LP_ID=${LP_ID:-43}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_DIR="${SCRIPT_DIR}/dump"
mkdir -p "${DUMP_DIR}"

PG_EXEC="docker exec -i java-oj-postgres psql -U onlinejudge -d alethicode -At -c"

run_copy() {
  local table=$1
  local select_expr=$2
  local out="${DUMP_DIR}/${table}.csv"
  ${PG_EXEC} "COPY (${select_expr}) TO STDOUT WITH (FORMAT CSV, HEADER true)" > "${out}"
  local n
  n=$(${PG_EXEC} "SELECT count(*) FROM (${select_expr}) AS t")
  echo "[dump] ${table}: ${n} rows -> ${out}"
}

TASK_IDS_SQL="(SELECT id FROM language_pack_init_task WHERE language_pack_id=${LP_ID})"
KC_IDS_SQL="(SELECT id FROM language_pack_kc WHERE language_pack_id=${LP_ID})"
EX_IDS_SQL="(SELECT id FROM language_pack_example WHERE language_pack_id=${LP_ID})"
PROB_IDS_SQL="(SELECT problem_id FROM language_pack_problem_mapping WHERE language_pack_id=${LP_ID})"

# language_pack 主表：creator_id 临时置 NULL，导入后由 ECS 端 UPDATE 设回 root.id
run_copy language_pack \
  "SELECT id, slug, version, name, primary_language, description, status,
          document_count, page_count, chapter_count, kc_count,
          example_count, problem_count,
          create_time, update_time, course_objective, target_audience,
          total_hours, NULL::bigint AS creator_id
   FROM language_pack WHERE id=${LP_ID}"

run_copy language_pack_init_task         "SELECT * FROM language_pack_init_task         WHERE language_pack_id=${LP_ID}"
run_copy language_pack_init_stage_log    "SELECT * FROM language_pack_init_stage_log    WHERE task_id IN ${TASK_IDS_SQL}"
run_copy language_pack_init_artifact     "SELECT * FROM language_pack_init_artifact     WHERE task_id IN ${TASK_IDS_SQL}"
run_copy language_pack_init_agent_run    "SELECT * FROM language_pack_init_agent_run    WHERE task_id IN ${TASK_IDS_SQL}"
run_copy language_pack_init_batch_run    "SELECT * FROM language_pack_init_batch_run    WHERE task_id IN ${TASK_IDS_SQL}"
run_copy language_pack_init_quality_report "SELECT * FROM language_pack_init_quality_report WHERE language_pack_id=${LP_ID}"

run_copy language_pack_document          "SELECT * FROM language_pack_document          WHERE language_pack_id=${LP_ID}"
run_copy language_pack_page              "SELECT * FROM language_pack_page              WHERE language_pack_id=${LP_ID}"
run_copy language_pack_chapter           "SELECT * FROM language_pack_chapter           WHERE language_pack_id=${LP_ID}"
run_copy language_pack_kc                "SELECT * FROM language_pack_kc                WHERE language_pack_id=${LP_ID}"
run_copy language_pack_kc_page_mapping   "SELECT * FROM language_pack_kc_page_mapping   WHERE kc_id IN ${KC_IDS_SQL}"
run_copy language_pack_kc_prerequisite   "SELECT * FROM language_pack_kc_prerequisite   WHERE language_pack_id=${LP_ID}"
run_copy language_pack_example           "SELECT * FROM language_pack_example           WHERE language_pack_id=${LP_ID}"
run_copy language_pack_example_kc_mapping "SELECT * FROM language_pack_example_kc_mapping WHERE example_id IN ${EX_IDS_SQL}"
run_copy language_pack_problem_generation_log "SELECT * FROM language_pack_problem_generation_log WHERE language_pack_id=${LP_ID}"
run_copy language_pack_review_task       "SELECT * FROM language_pack_review_task       WHERE language_pack_id=${LP_ID}"
run_copy language_pack_problem_mapping   "SELECT * FROM language_pack_problem_mapping   WHERE language_pack_id=${LP_ID}"

run_copy problem_tag                     "SELECT * FROM problem_tag                     WHERE id IN (SELECT problemtag_id FROM problem_problem_tags WHERE problem_id IN ${PROB_IDS_SQL})"
run_copy problem                         "SELECT * FROM problem                         WHERE id IN ${PROB_IDS_SQL}"
run_copy problem_problem_tags            "SELECT * FROM problem_problem_tags            WHERE problem_id IN ${PROB_IDS_SQL}"

run_copy ai_knowledge_component          "SELECT * FROM ai_knowledge_component          WHERE language_pack_id=${LP_ID}"
run_copy ai_problem_kc_mapping           "SELECT * FROM ai_problem_kc_mapping           WHERE problem_id IN ${PROB_IDS_SQL}"

# 路径替换：本地容器外路径 -> ECS 容器内路径
sed -i 's|/home/cypress/Alethicode/deploy/data/language_pack/|/data/language_pack/|g' \
  "${DUMP_DIR}/language_pack_document.csv" \
  "${DUMP_DIR}/language_pack_page.csv"

echo
echo "[done] business csv 写入 ${DUMP_DIR}/"
ls -la "${DUMP_DIR}"

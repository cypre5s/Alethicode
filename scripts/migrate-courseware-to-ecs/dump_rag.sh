#!/usr/bin/env bash
set -euo pipefail

# 把本地 LightRAG (PostgreSQL `lightrag_*` 表 + Memgraph `alethicode` workspace)
# 全 dump 到本地 dump/ 目录, 后续 scp 到 ECS 后再导入. 范围 = workspace=alethicode 全部, 不再按
# language_pack_id 过滤 (LightRAG 内部按内容 hash 去重, 强行按 page 过滤会撕裂引用关系).
# v4 (text_embedding_v4) 表本地是 0 行, 显式排除以避免 ECS 端缺表 INSERT 失败.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_DIR="${SCRIPT_DIR}/dump"
mkdir -p "${DUMP_DIR}"

LIGHTRAG_DUMP="${DUMP_DIR}/lightrag_dump.sql"
MEMGRAPH_DUMP="${DUMP_DIR}/memgraph_dump.cypher"

echo "[dump] lightrag_* 表 (排除 text_embedding_v4 系列)"
{
  echo "-- LightRAG data dump (workspace=alethicode), 由 dump_rag.sh 生成"
  echo "-- 头部 TRUNCATE 保证 idempotent; 重跑覆盖既有 alethicode workspace 数据"
  echo "BEGIN;"
  for t in lightrag_llm_cache lightrag_doc_status lightrag_doc_full lightrag_doc_chunks \
           lightrag_full_entities lightrag_full_relations \
           lightrag_entity_chunks lightrag_relation_chunks \
           lightrag_vdb_chunks_embedding_3_2048d \
           lightrag_vdb_entity_embedding_3_2048d \
           lightrag_vdb_relation_embedding_3_2048d; do
    echo "DELETE FROM ${t} WHERE workspace IN ('alethicode','alethicode_smoke');"
  done
  echo "COMMIT;"
  echo
  docker exec java-oj-postgres pg_dump \
    -U onlinejudge -d alethicode \
    --data-only --column-inserts \
    --no-owner --no-privileges \
    -t 'lightrag_doc_full' -t 'lightrag_doc_chunks' -t 'lightrag_doc_status' \
    -t 'lightrag_full_entities' -t 'lightrag_full_relations' \
    -t 'lightrag_entity_chunks' -t 'lightrag_relation_chunks' \
    -t 'lightrag_llm_cache' \
    -t 'lightrag_vdb_chunks_embedding_3_2048d' \
    -t 'lightrag_vdb_entity_embedding_3_2048d' \
    -t 'lightrag_vdb_relation_embedding_3_2048d'
} > "${LIGHTRAG_DUMP}"
echo "  -> $(wc -c < "${LIGHTRAG_DUMP}" | awk '{print int($1/1024/1024)" MB"}') ${LIGHTRAG_DUMP}"

echo "[dump] Memgraph DUMP DATABASE (整图 cypher 命令)"
{
  echo "// Memgraph data dump, 由 dump_rag.sh 生成 (mgconsole DUMP DATABASE)"
  echo "// 头部清旧 alethicode 数据保证 idempotent; 不动其它 workspace"
  echo "MATCH (n:alethicode) DETACH DELETE n;"
  echo

  docker exec -i java-oj-memgraph mgconsole --output-format=csv \
    <<< 'DUMP DATABASE;' 2>&1 \
    | python3 -c '
import csv, sys
# mgconsole 把 cypher 命令作为单列字符串字段输出, 所以一行原始 csv 看上去是
#   "\"CREATE (...);\""
# csv.reader 剥掉最外层 csv 引号 + 把 doublequote 还原, row[0] 仍是
#   "CREATE (...);"
# 还需要做两步: (1) 剥掉这层 mgconsole 加的引号；(2) 反义内部的 \"/\\/\\\x27.
reader = csv.reader(sys.stdin)
for row in reader:
    if not row or row[0] == "QUERY":
        continue
    s = row[0]
    if len(s) >= 2 and s[0] == "\"" and s[-1] == "\"":
        s = s[1:-1]
    s = s.replace("\\\\", "\x00").replace("\\\"", "\"").replace("\\\x27", "\x27").replace("\x00", "\\")
    s = s.strip()
    if s:
        print(s if s.endswith(";") else s + ";")
'
} > "${MEMGRAPH_DUMP}"
echo "  -> $(wc -l < "${MEMGRAPH_DUMP}") lines ${MEMGRAPH_DUMP}"

echo
ls -la "${DUMP_DIR}"/lightrag_dump.sql "${DUMP_DIR}"/memgraph_dump.cypher

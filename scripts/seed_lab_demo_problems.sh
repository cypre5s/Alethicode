#!/usr/bin/env bash
# ============================================================
# AI Tutor Lab — 预导入 3 道 Python 典型错误场景例题
#
# 为 /ai-tutor-lab 工作台提供"真实判题闭环"所需的题目：
#   LAB_DEMO_INDENT : 缩进错误场景（两数之和）
#   LAB_DEMO_TYPE   : 类型错误场景（字符串没 int 转换）
#   LAB_DEMO_BOUND  : 边界错误场景（数组越界）
#
# 脚本做两件事：
#   1. 在 ${TEST_CASE_DIR} 下创建 3 个 testcase 目录（UUID 名），
#      写入 1.in / 1.out / 2.in / 2.out / info（含 stripped_output_md5）
#   2. 执行幂等 SQL，把 3 道题插入 problem 表（display_id=LAB_DEMO_*）
#
# 幂等策略：problem._id 没有 UNIQUE 约束，所以采用
#   UPDATE ... WHERE _id=X → 若 0 行影响则 INSERT 的 UPSERT 模式
#
# 用法：
#   bash scripts/seed_lab_demo_problems.sh
#   bash scripts/seed_lab_demo_problems.sh --db-url "postgresql://..." --test-case-dir "/path"
# ============================================================
set -euo pipefail

DB_URL="${DB_URL:-postgresql://root:root123456@127.0.0.1:5436/alethicode}"
TEST_CASE_DIR="${TEST_CASE_DIR:-/home/cypress/Alethicode/deploy/data/test_case}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db-url) DB_URL="$2"; shift 2;;
    --test-case-dir) TEST_CASE_DIR="$2"; shift 2;;
    -h|--help) grep '^#' "$0"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

log() { echo "[lab-seed] $*"; }

log "DB: $DB_URL"
log "TEST_CASE_DIR: $TEST_CASE_DIR"

if [[ ! -d "$TEST_CASE_DIR" ]]; then
  log "ERROR: TEST_CASE_DIR does not exist: $TEST_CASE_DIR"
  exit 2
fi

UUID_INDENT="lab000001000000000000000000000001"
UUID_TYPE="lab000001000000000000000000000002"
UUID_BOUND="lab000001000000000000000000000003"

write_info() {
  local dir="$1"
  local count="$2"
  python3 - "$dir" "$count" <<'PY'
import hashlib, json, os, sys
base = sys.argv[1]
count = int(sys.argv[2])
cases = {}
for i in range(1, count + 1):
    with open(os.path.join(base, f"{i}.in"), "rb") as f:
        in_bytes = f.read()
    with open(os.path.join(base, f"{i}.out"), "rb") as f:
        out_bytes = f.read()
    stripped = out_bytes.rstrip()
    md5 = hashlib.md5(stripped).hexdigest()
    cases[str(i)] = {
        "input_name": f"{i}.in",
        "input_size": len(in_bytes),
        "output_name": f"{i}.out",
        "output_size": len(out_bytes),
        "stripped_output_md5": md5,
    }
info = {"spj": False, "test_cases": cases}
with open(os.path.join(base, "info"), "w", encoding="utf-8") as f:
    json.dump(info, f, ensure_ascii=False)
PY
}

setup_indent_cases() {
  local dir="$TEST_CASE_DIR/$UUID_INDENT"
  mkdir -p "$dir"
  printf -- '%s\n' '3' '5'   > "$dir/1.in"
  printf -- '%s\n' '8'        > "$dir/1.out"
  printf -- '%s\n' '12' '13' > "$dir/2.in"
  printf -- '%s\n' '25'       > "$dir/2.out"
  printf -- '%s\n' '-7' '7'  > "$dir/3.in"
  printf -- '%s\n' '0'        > "$dir/3.out"
  write_info "$dir" 3
  log "  testcase written: $dir"
}

setup_type_cases() {
  local dir="$TEST_CASE_DIR/$UUID_TYPE"
  mkdir -p "$dir"
  printf -- '%s\n' '4'  > "$dir/1.in"
  printf -- '%s\n' '16' > "$dir/1.out"
  printf -- '%s\n' '9'  > "$dir/2.in"
  printf -- '%s\n' '81' > "$dir/2.out"
  printf -- '%s\n' '0'  > "$dir/3.in"
  printf -- '%s\n' '0'  > "$dir/3.out"
  write_info "$dir" 3
  log "  testcase written: $dir"
}

setup_bound_cases() {
  local dir="$TEST_CASE_DIR/$UUID_BOUND"
  mkdir -p "$dir"
  printf -- '%s\n' '5 3' '10 20 30 40 50' > "$dir/1.in"
  printf -- '%s\n' '30' > "$dir/1.out"
  printf -- '%s\n' '3 1' '7 8 9' > "$dir/2.in"
  printf -- '%s\n' '7'  > "$dir/2.out"
  printf -- '%s\n' '4 4' '1 2 3 4' > "$dir/3.in"
  printf -- '%s\n' '4'  > "$dir/3.out"
  write_info "$dir" 3
  log "  testcase written: $dir"
}

log "=== 写入 testcase 文件 ==="
setup_indent_cases
setup_type_cases
setup_bound_cases

log "=== 写入 SQL ==="
psql "$DB_URL" <<SEED_SQL
DO \$\$
DECLARE
    v_owner_id BIGINT;
BEGIN
    SELECT id INTO v_owner_id FROM "user" WHERE username='root' LIMIT 1;
    IF v_owner_id IS NULL THEN
        SELECT id INTO v_owner_id FROM "user" ORDER BY id LIMIT 1;
    END IF;
    IF v_owner_id IS NULL THEN
        RAISE NOTICE 'No user available, Lab demo problems seed skipped';
        RETURN;
    END IF;

    -- ═══════════════════════════════════════════════════════════
    -- 题 1：LAB_DEMO_INDENT（缩进错误场景）
    -- ═══════════════════════════════════════════════════════════
    UPDATE problem SET
        is_public=true, visible=true,
        test_case_id='$UUID_INDENT',
        title='Lab 示例：两数之和',
        description='<p>输入两个整数 a 和 b（每行一个），输出 a + b。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>缩进错误</strong>类常见坑。</p>',
        input_description='<p>两行，每行一个整数。</p>',
        output_description='<p>一个整数，表示两数之和。</p>',
        samples='[{"input": "3\n5\n", "output": "8\n"}, {"input": "12\n13\n", "output": "25\n"}]'::jsonb,
        test_case_score='[{"input_name":"1.in","output_name":"1.out","score":30},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":40}]'::jsonb,
        hint='提示：两行分别读入后转 int 相加即可。注意缩进要一致。',
        languages='["Python3"]'::jsonb,
        time_limit=1000, memory_limit=256,
        last_update_time=NOW()
    WHERE _id='LAB_DEMO_INDENT';

    IF NOT FOUND THEN
        INSERT INTO problem (
            _id, is_public, title, description, input_description, output_description,
            samples, test_case_id, test_case_score, hint, languages,
            time_limit, memory_limit, visible, difficulty, source, created_by_id,
            template, statistic_info, create_time
        ) VALUES (
            'LAB_DEMO_INDENT', true,
            'Lab 示例：两数之和',
            '<p>输入两个整数 a 和 b（每行一个），输出 a + b。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>缩进错误</strong>类常见坑。</p>',
            '<p>两行，每行一个整数。</p>',
            '<p>一个整数，表示两数之和。</p>',
            '[{"input": "3\n5\n", "output": "8\n"}, {"input": "12\n13\n", "output": "25\n"}]'::jsonb,
            '$UUID_INDENT',
            '[{"input_name":"1.in","output_name":"1.out","score":30},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":40}]'::jsonb,
            '提示：两行分别读入后转 int 相加即可。注意缩进要一致。',
            '["Python3"]'::jsonb,
            1000, 256, true, 'Low', 'AI Tutor Lab', v_owner_id,
            '{}'::jsonb, '{}'::jsonb, NOW()
        );
    END IF;

    -- ═══════════════════════════════════════════════════════════
    -- 题 2：LAB_DEMO_TYPE（类型错误场景）
    -- ═══════════════════════════════════════════════════════════
    UPDATE problem SET
        is_public=true, visible=true,
        test_case_id='$UUID_TYPE',
        title='Lab 示例：求整数平方',
        description='<p>输入一个整数 n，输出 n 的平方。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>类型错误</strong>类常见坑（input() 返回 str）。</p>',
        input_description='<p>一行一个整数 n（-1000 ≤ n ≤ 1000）。</p>',
        output_description='<p>一个整数，表示 n × n。</p>',
        samples='[{"input": "4\n", "output": "16\n"}, {"input": "9\n", "output": "81\n"}]'::jsonb,
        test_case_score='[{"input_name":"1.in","output_name":"1.out","score":40},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":30}]'::jsonb,
        hint='提示：input() 返回字符串，需要先 int() 再乘方。',
        languages='["Python3"]'::jsonb,
        time_limit=1000, memory_limit=256,
        last_update_time=NOW()
    WHERE _id='LAB_DEMO_TYPE';

    IF NOT FOUND THEN
        INSERT INTO problem (
            _id, is_public, title, description, input_description, output_description,
            samples, test_case_id, test_case_score, hint, languages,
            time_limit, memory_limit, visible, difficulty, source, created_by_id,
            template, statistic_info, create_time
        ) VALUES (
            'LAB_DEMO_TYPE', true,
            'Lab 示例：求整数平方',
            '<p>输入一个整数 n，输出 n 的平方。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>类型错误</strong>类常见坑（input() 返回 str）。</p>',
            '<p>一行一个整数 n（-1000 ≤ n ≤ 1000）。</p>',
            '<p>一个整数，表示 n × n。</p>',
            '[{"input": "4\n", "output": "16\n"}, {"input": "9\n", "output": "81\n"}]'::jsonb,
            '$UUID_TYPE',
            '[{"input_name":"1.in","output_name":"1.out","score":40},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":30}]'::jsonb,
            '提示：input() 返回字符串，需要先 int() 再乘方。',
            '["Python3"]'::jsonb,
            1000, 256, true, 'Low', 'AI Tutor Lab', v_owner_id,
            '{}'::jsonb, '{}'::jsonb, NOW()
        );
    END IF;

    -- ═══════════════════════════════════════════════════════════
    -- 题 3：LAB_DEMO_BOUND（边界错误场景）
    -- ═══════════════════════════════════════════════════════════
    UPDATE problem SET
        is_public=true, visible=true,
        test_case_id='$UUID_BOUND',
        title='Lab 示例：取第 k 个数',
        description='<p>输入第一行两个整数 n 和 k（1 ≤ k ≤ n ≤ 100），第二行 n 个整数，输出第 k 个（<strong>1-indexed</strong>）。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>数组越界</strong>类常见坑（Python 索引从 0 开始）。</p>',
        input_description='<p>第一行 n k；第二行 n 个整数。</p>',
        output_description='<p>一个整数，即第 k 个数（1-indexed）。</p>',
        samples='[{"input": "5 3\n10 20 30 40 50\n", "output": "30\n"}, {"input": "3 1\n7 8 9\n", "output": "7\n"}]'::jsonb,
        test_case_score='[{"input_name":"1.in","output_name":"1.out","score":40},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":30}]'::jsonb,
        hint='提示：题目说 1-indexed，但 Python 列表从 0 开始。',
        languages='["Python3"]'::jsonb,
        time_limit=1000, memory_limit=256,
        last_update_time=NOW()
    WHERE _id='LAB_DEMO_BOUND';

    IF NOT FOUND THEN
        INSERT INTO problem (
            _id, is_public, title, description, input_description, output_description,
            samples, test_case_id, test_case_score, hint, languages,
            time_limit, memory_limit, visible, difficulty, source, created_by_id,
            template, statistic_info, create_time
        ) VALUES (
            'LAB_DEMO_BOUND', true,
            'Lab 示例：取第 k 个数',
            '<p>输入第一行两个整数 n 和 k（1 ≤ k ≤ n ≤ 100），第二行 n 个整数，输出第 k 个（<strong>1-indexed</strong>）。</p><p>这是 AI Tutor Lab 的内置示范题，用来演示<strong>数组越界</strong>类常见坑（Python 索引从 0 开始）。</p>',
            '<p>第一行 n k；第二行 n 个整数。</p>',
            '<p>一个整数，即第 k 个数（1-indexed）。</p>',
            '[{"input": "5 3\n10 20 30 40 50\n", "output": "30\n"}, {"input": "3 1\n7 8 9\n", "output": "7\n"}]'::jsonb,
            '$UUID_BOUND',
            '[{"input_name":"1.in","output_name":"1.out","score":40},{"input_name":"2.in","output_name":"2.out","score":30},{"input_name":"3.in","output_name":"3.out","score":30}]'::jsonb,
            '提示：题目说 1-indexed，但 Python 列表从 0 开始。',
            '["Python3"]'::jsonb,
            1000, 256, true, 'Low', 'AI Tutor Lab', v_owner_id,
            '{}'::jsonb, '{}'::jsonb, NOW()
        );
    END IF;

    RAISE NOTICE 'Lab demo problems seeded with owner %', v_owner_id;
END
\$\$;

INSERT INTO sys_options(key, value, updated_at) VALUES (
    'lab_demo_problems',
    '["LAB_DEMO_INDENT","LAB_DEMO_TYPE","LAB_DEMO_BOUND"]'::jsonb,
    now()
) ON CONFLICT (key) DO UPDATE SET value=EXCLUDED.value, updated_at=now();
SEED_SQL

log "=== 完成 ==="
log "Lab 工作台 3 道示范题已就位：LAB_DEMO_INDENT / LAB_DEMO_TYPE / LAB_DEMO_BOUND"
log "前端 /ai-tutor-lab 将通过 GET /api/ai/tutor/lab/demo-problems 获取这 3 道题"

#!/usr/bin/env bash
set -euo pipefail

# 在 ECS 上直接运行，一键配置 deploy/.env 并创建超级管理员。
#
# 用法: bash scripts/ecs_setup.sh
#
# 安全重要提示：
# - 本脚本"不再"硬编码任何 API Key / 密码 / Token，历史版本曾将真实密钥写入
#   仓库，属于严重安全事故，请立即到各云厂商控制台吊销并轮换以下凭证：
#     OPENAI_API_KEY / INIT_LLM_API_KEY / EMBEDDING_API_KEY /
#     JUDGE_SERVER_TOKEN / 超级管理员 lbx 的登录密码。
# - 运行本脚本前，请先将真实密钥写入 $DEPLOY_DIR/.env.local（此文件已被
#   .gitignore 忽略），脚本会将其中的变量渲染到 deploy/.env。
# - 如果没有 .env.local，脚本会生成一份只含占位符的 deploy/.env，
#   你需要手动填入真实值后再启动服务。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
SECRETS_FILE="$DEPLOY_DIR/.env.local"

echo "=== Alethicode ECS 一键配置 ==="
echo "[INFO] 项目目录: $ROOT_DIR"

mkdir -p "$DEPLOY_DIR"

# 如果存在 .env.local（推荐做法：只保留在 ECS 本地，不进仓库），
# 则优先从中加载真实密钥，避免脚本本身泄露敏感信息。
if [[ -f "$SECRETS_FILE" ]]; then
  echo "[INFO] 发现 $SECRETS_FILE，加载真实密钥"
  set -a
  # shellcheck disable=SC1090
  source "$SECRETS_FILE"
  set +a
else
  echo "[WARN] 未发现 $SECRETS_FILE，将生成含占位符的 deploy/.env"
  echo "[WARN] 请在脚本执行后手动填入真实 API Key / Token，否则服务无法正常工作"
fi

# 默认值全部是占位符，真实值通过上面 source 到环境变量或执行前 export 注入。
: "${DB_PASSWORD:=REPLACE_WITH_DB_PASSWORD}"
: "${REDIS_PASSWORD:=REPLACE_WITH_REDIS_PASSWORD}"
: "${JUDGE_SERVER_TOKEN:=dev-judge-token-change-me}"
: "${OPENAI_API_KEY:=REPLACE_WITH_OPENAI_API_KEY}"
: "${EMBEDDING_API_KEY:=REPLACE_WITH_EMBEDDING_API_KEY}"
: "${EMBEDDING_BASE_URL:=https://dashscope.aliyuncs.com/compatible-mode/v1}"
: "${EMBEDDING_MODEL:=text-embedding-v4}"
: "${LLM_MODEL:=deepseek-v4-flash}"
: "${LLM_BASE_URL:=https://api.deepseek.com}"
: "${LLM_API_TIMEOUT_SECONDS:=300}"
: "${LLM_API_MAX_RETRIES:=5}"
: "${INIT_LLM_API_KEY:=REPLACE_WITH_INIT_LLM_API_KEY}"
: "${INIT_LLM_MODEL:=deepseek-v4-flash}"
: "${INIT_LLM_BASE_URL:=https://api.deepseek.com}"
: "${TUTOR_REACT_ENABLED:=false}"
: "${TUTOR_REACT_MAX_ITERATIONS:=4}"
: "${QA_REACT_ENABLED:=false}"
: "${QA_REACT_MAX_ITERATIONS:=3}"
: "${VIDEO_TTS_PROVIDER:=stub}"
: "${VIDEO_RENDER_PROVIDER:=stub}"
: "${ADMIN_PASSWORD_BCRYPT:=}"

cat > "$ENV_FILE" <<ENVEOF
# 数据库与 Redis
DB_PASSWORD=${DB_PASSWORD}
REDIS_PASSWORD=${REDIS_PASSWORD}
JUDGE_SERVER_TOKEN=${JUDGE_SERVER_TOKEN}

# 容器内运行时路径
TEST_CASE_DIR=/test_case
UPLOAD_DIR=/data/public/upload
CLASSROOM_LESSON_DIR=/data/classroom_lessons
SUBMISSION_DATA_DIR=/data/submission
LANGUAGE_PACK_STORAGE_ROOT=/data/language_pack
LANGUAGE_PACK_PREVIEW_DIR=/data/language_pack/preview

# AI Provider
OPENAI_API_KEY=${OPENAI_API_KEY}
EMBEDDING_API_KEY=${EMBEDDING_API_KEY}
EMBEDDING_BASE_URL=${EMBEDDING_BASE_URL}
EMBEDDING_MODEL=${EMBEDDING_MODEL}
LLM_MODEL=${LLM_MODEL}
LLM_BASE_URL=${LLM_BASE_URL}
LLM_API_TIMEOUT_SECONDS=${LLM_API_TIMEOUT_SECONDS}
LLM_API_MAX_RETRIES=${LLM_API_MAX_RETRIES}

# 初始化 LLM
INIT_LLM_API_KEY=${INIT_LLM_API_KEY}
INIT_LLM_MODEL=${INIT_LLM_MODEL}
INIT_LLM_BASE_URL=${INIT_LLM_BASE_URL}

# Agent 架构
TUTOR_REACT_ENABLED=${TUTOR_REACT_ENABLED}
TUTOR_REACT_MAX_ITERATIONS=${TUTOR_REACT_MAX_ITERATIONS}
QA_REACT_ENABLED=${QA_REACT_ENABLED}
QA_REACT_MAX_ITERATIONS=${QA_REACT_MAX_ITERATIONS}

# 视频生成
VIDEO_TTS_PROVIDER=${VIDEO_TTS_PROVIDER}
VIDEO_RENDER_PROVIDER=${VIDEO_RENDER_PROVIDER}
ENVEOF

echo "[OK] deploy/.env 已写入"

# 创建超级管理员 lbx 时，要求通过 ADMIN_PASSWORD_BCRYPT 环境变量显式注入
# BCrypt 密码哈希，避免在仓库里出现任何明文或固定哈希。生成命令示例：
#   htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n'
if [[ -z "$ADMIN_PASSWORD_BCRYPT" ]]; then
  echo "[WARN] 未提供 ADMIN_PASSWORD_BCRYPT，跳过创建/更新超级管理员 lbx"
  echo "[HINT] 生成 BCrypt 密码后通过环境变量注入，例如："
  echo "       ADMIN_PASSWORD_BCRYPT=\$(htpasswd -bnBC 10 '' 'YourPassword' | tr -d ':\\n') bash scripts/ecs_setup.sh"
else
  echo "[INFO] 等待数据库容器就绪..."
  for _ in $(seq 1 30); do
    if docker exec java-oj-postgres pg_isready -U onlinejudge -d alethicode >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  if docker exec java-oj-postgres pg_isready -U onlinejudge -d alethicode >/dev/null 2>&1; then
    echo "[INFO] 创建/更新超级管理员 lbx ..."
    # 通过 psql 变量传入 bcrypt 哈希，防止在 shell 侧出现 $2b$ 解析问题
    docker exec -i -e ADMIN_PASSWORD_BCRYPT="$ADMIN_PASSWORD_BCRYPT" java-oj-postgres \
      psql -U onlinejudge -d alethicode -v admin_pw="${ADMIN_PASSWORD_BCRYPT}" <<'SQL'
DO $$
DECLARE
  uid BIGINT;
  pw  TEXT := :'admin_pw';
BEGIN
  SELECT id INTO uid FROM "user" WHERE lower(username) = 'lbx';
  IF uid IS NULL THEN
    INSERT INTO "user" (username, email, password_hash, admin_type, problem_permission, is_disabled, create_time)
    VALUES ('lbx', 'lbx@admin.local', pw, 'Admin', 'All', false, now())
    RETURNING id INTO uid;
    RAISE NOTICE 'User created with id: %', uid;
  ELSE
    UPDATE "user"
       SET admin_type = 'Admin', problem_permission = 'All', is_disabled = false, password_hash = pw
     WHERE id = uid;
    RAISE NOTICE 'User lbx already exists (id: %), updated to Admin', uid;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM user_profile WHERE user_id = uid) THEN
    INSERT INTO user_profile (user_id, acm_problems_status, oi_problems_status, role)
    VALUES (uid, '{}', '{}', 'Admin(Super)');
    RAISE NOTICE 'Profile created';
  END IF;
END $$;
SQL
    echo "[OK] 超级管理员 lbx 已就绪（密码由 ADMIN_PASSWORD_BCRYPT 注入）"
  else
    echo "[WARN] 数据库未就绪，跳过创建管理员。启动服务后重新运行此脚本即可。"
  fi
fi

echo ""
echo "=== 配置完成 ==="
echo "下一步: cd $DEPLOY_DIR && docker compose up -d --build"

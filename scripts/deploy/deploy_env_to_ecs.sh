#!/usr/bin/env bash
set -euo pipefail

# 从本地 backend/.env 读取密钥，生成 ECS deploy/.env 并通过 SSH 写入
# 用法: bash scripts/deploy/deploy_env_to_ecs.sh root@你的ECS_IP

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BACKEND_ENV="$ROOT_DIR/backend/.env"

if [[ ! -f "$BACKEND_ENV" ]]; then
  echo "[ERROR] 未找到 $BACKEND_ENV" >&2
  exit 1
fi

ECS_HOST="${1:-}"
if [[ -z "$ECS_HOST" ]]; then
  echo "用法: $0 root@ECS_IP"
  echo "示例: $0 root@101.37.68.196"
  exit 1
fi

set -a
source "$BACKEND_ENV"
set +a

DEPLOY_ENV_CONTENT="$(cat <<ENVEOF
# === Database & Redis ===
DB_PASSWORD=${DB_PASSWORD:-alethicode_db_local_2026}
REDIS_PASSWORD=${REDIS_PASSWORD:-alethicode_redis_local_2026}
JUDGE_SERVER_TOKEN=${JUDGE_SERVER_TOKEN:-dev-judge-token-change-me}

# === Runtime paths inside containers ===
TEST_CASE_DIR=/test_case
UPLOAD_DIR=/data/public/upload
CLASSROOM_LESSON_DIR=/data/classroom_lessons
SUBMISSION_DATA_DIR=/data/submission
LANGUAGE_PACK_STORAGE_ROOT=/data/language_pack
LANGUAGE_PACK_PREVIEW_DIR=/data/language_pack/preview

# === AI Provider ===
OPENAI_API_KEY=${OPENAI_API_KEY:-}
EMBEDDING_API_KEY=${EMBEDDING_API_KEY:-}
EMBEDDING_BASE_URL=${EMBEDDING_BASE_URL:-https://dashscope.aliyuncs.com/compatible-mode/v1}
EMBEDDING_MODEL=${EMBEDDING_MODEL:-text-embedding-v4}
LLM_MODEL=${LLM_MODEL:-deepseek-v4-flash}
LLM_BASE_URL=${LLM_BASE_URL:-https://api.deepseek.com}
LLM_API_TIMEOUT_SECONDS=${LLM_API_TIMEOUT_SECONDS:-300}
LLM_API_MAX_RETRIES=${LLM_API_MAX_RETRIES:-5}

# === Init LLM ===
INIT_LLM_API_KEY=${INIT_LLM_API_KEY:-}
INIT_LLM_MODEL=${INIT_LLM_MODEL:-deepseek-v4-flash}
INIT_LLM_BASE_URL=${INIT_LLM_BASE_URL:-https://api.deepseek.com}

# === Agent Architecture ===
TUTOR_REACT_ENABLED=${TUTOR_REACT_ENABLED:-false}
TUTOR_REACT_MAX_ITERATIONS=${TUTOR_REACT_MAX_ITERATIONS:-4}
QA_REACT_ENABLED=${QA_REACT_ENABLED:-false}
QA_REACT_MAX_ITERATIONS=${QA_REACT_MAX_ITERATIONS:-3}

# === Video Generation ===
VIDEO_TTS_PROVIDER=${VIDEO_TTS_PROVIDER:-stub}
VIDEO_RENDER_PROVIDER=${VIDEO_RENDER_PROVIDER:-stub}
ENVEOF
)"

echo "[INFO] 目标: $ECS_HOST"
echo "[INFO] 写入 /opt/Alethicode/deploy/.env ..."

ssh "$ECS_HOST" "mkdir -p /opt/Alethicode/deploy && cat > /opt/Alethicode/deploy/.env" <<< "$DEPLOY_ENV_CONTENT"

# 为避免把任何凭证（包括 BCrypt 哈希）写进仓库，创建/更新超级管理员的步骤
# 改为：要求通过 ADMIN_PASSWORD_BCRYPT 环境变量显式注入新的哈希值。
# 生成方式示例：htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n'
if [[ -z "${ADMIN_PASSWORD_BCRYPT:-}" ]]; then
  echo "[WARN] 未提供 ADMIN_PASSWORD_BCRYPT，跳过创建/更新超级管理员 lbx"
  echo "[HINT] 示例: ADMIN_PASSWORD_BCRYPT=\$(htpasswd -bnBC 10 '' 'YourPassword' | tr -d ':\\n') \\"
  echo "       bash scripts/deploy/deploy_env_to_ecs.sh $ECS_HOST"
else
  echo "[INFO] 创建/更新超级管理员 lbx ..."
  ssh "$ECS_HOST" \
    "docker exec -i -e ADMIN_PASSWORD_BCRYPT='${ADMIN_PASSWORD_BCRYPT}' java-oj-postgres psql -U onlinejudge -d alethicode -v admin_pw=\"\$ADMIN_PASSWORD_BCRYPT\"" << 'SQL'
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
    RAISE NOTICE 'User lbx already exists with id: %', uid;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM user_profile WHERE user_id = uid) THEN
    INSERT INTO user_profile (user_id, acm_problems_status, oi_problems_status, role)
    VALUES (uid, '{}', '{}', 'Admin(Super)');
    RAISE NOTICE 'Profile created';
  END IF;
END $$;
SQL
fi

echo "[OK] 配置完成"
echo "  deploy/.env 已写入"
echo "  超级管理员 lbx 已创建（密码请通过数据库 BCrypt 哈希自行设置，不在脚本中明文输出）"
echo ""
echo "如需重启服务使配置生效："
echo "  ssh $ECS_HOST 'cd /opt/Alethicode/deploy && docker compose up -d --build'"

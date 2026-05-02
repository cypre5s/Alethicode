#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${1:-}"
EMAIL="${2:-}"

if [[ -z "$DOMAIN" || -z "$EMAIL" ]]; then
  echo "用法: $0 <域名> <邮箱>"
  echo "示例: $0 oj.example.com admin@example.com"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
CERTBOT_WWW="$DEPLOY_DIR/data/certbot/www"
CERTBOT_CONF="$DEPLOY_DIR/data/certbot/conf"

mkdir -p "$CERTBOT_WWW" "$CERTBOT_CONF"

echo "=== Step 1: 获取 Let's Encrypt 证书 ==="
echo "[INFO] 域名: $DOMAIN"
echo "[INFO] 邮箱: $EMAIL"
echo "[INFO] 确保域名 A 记录已指向本机 IP，且 80 端口对外开放"

docker run --rm \
  -v "$CERTBOT_CONF:/etc/letsencrypt" \
  -v "$CERTBOT_WWW:/var/www/certbot" \
  -p 80:80 \
  certbot/certbot certonly \
    --standalone \
    --preferred-challenges http \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN"

if [[ ! -f "$CERTBOT_CONF/live/$DOMAIN/fullchain.pem" ]]; then
  echo "[ERROR] 证书获取失败" >&2
  exit 1
fi

echo ""
echo "=== Step 2: 写入 DOMAIN 到 .env ==="
if grep -q '^DOMAIN=' "$DEPLOY_DIR/.env" 2>/dev/null; then
  sed -i "s|^DOMAIN=.*|DOMAIN=$DOMAIN|" "$DEPLOY_DIR/.env"
else
  echo "DOMAIN=$DOMAIN" >> "$DEPLOY_DIR/.env"
fi

echo ""
echo "=== Step 3: 配置 SSL nginx ==="
NGINX_SSL_CONF="$DEPLOY_DIR/nginx/ssl-frontend.conf"
sed "s|\${DOMAIN}|$DOMAIN|g" "$NGINX_SSL_CONF" > "$DEPLOY_DIR/nginx/ssl-frontend-rendered.conf"

echo ""
echo "[OK] 证书获取成功！"
echo ""
echo "接下来执行:"
echo "  cd $DEPLOY_DIR"
echo "  docker compose -f docker-compose.yml -f docker-compose.ssl.yml up -d --build"
echo ""
echo "自动续签（添加到 crontab）:"
echo "  0 3 * * 1 $ROOT_DIR/scripts/certbot_renew.sh $DOMAIN"

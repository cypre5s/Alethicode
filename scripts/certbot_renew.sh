#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${1:-}"
if [[ -z "$DOMAIN" ]]; then
  echo "用法: $0 <域名>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/../deploy" && pwd)"
CERTBOT_CONF="$DEPLOY_DIR/data/certbot/conf"
CERTBOT_WWW="$DEPLOY_DIR/data/certbot/www"

docker run --rm \
  -v "$CERTBOT_CONF:/etc/letsencrypt" \
  -v "$CERTBOT_WWW:/var/www/certbot" \
  certbot/certbot renew --webroot -w /var/www/certbot --quiet

docker exec java-oj-frontend nginx -s reload 2>/dev/null || true

echo "[OK] $(date): certbot renew done for $DOMAIN"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SEARCH_ROOT="$PROJECT_ROOT/src/pages/oj"

SCOPE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scope)
      SCOPE="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

build_paths() {
  local scope="$1"
  local paths=()
  IFS=',' read -ra PARTS <<< "$scope"
  for part in "${PARTS[@]}"; do
    part="$(echo "$part" | xargs)"
    local full="$PROJECT_ROOT/$part"
    if [[ -e "$full" ]]; then
      paths+=("$full")
    else
      echo "WARNING: path not found: $full" >&2
    fi
  done
  echo "${paths[@]}"
}

if [[ -n "$SCOPE" ]]; then
  read -ra TARGETS <<< "$(build_paths "$SCOPE")"
else
  TARGETS=("$SEARCH_ROOT")
fi

if [[ ${#TARGETS[@]} -eq 0 ]]; then
  echo "No valid target paths." >&2
  exit 2
fi

PATTERN='view-ui-plus|ViewUIPlus|\.ivu-'

count=0
for target in "${TARGETS[@]}"; do
  if [[ -d "$target" ]]; then
    n=$(rg -c "$PATTERN" --glob '*.vue' --glob '*.js' --glob '*.less' --glob '*.css' "$target" 2>/dev/null | awk -F: '{s+=$NF} END {print s+0}')
  elif [[ -f "$target" ]]; then
    n=$(rg -c "$PATTERN" "$target" 2>/dev/null | awk -F: '{s+=$NF} END {print s+0}')
  else
    n=0
  fi
  count=$((count + n))
done

cat <<EOF
{
  "pattern": "$PATTERN",
  "targets": "$(IFS=','; echo "${TARGETS[*]}")",
  "matches": $count
}
EOF

if [[ $count -ne 0 ]]; then
  exit 1
fi

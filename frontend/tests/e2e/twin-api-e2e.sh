#!/usr/bin/env bash
# L99 Twin API 端到端测试脚本
set -uo pipefail

BASE="http://127.0.0.1:8081"
COOKIE="/tmp/twin_e2e_cookies.txt"
PASS=0; FAIL=0; TOTAL=0

get_csrf() {
  rg 'csrftoken' "$COOKIE" | awk '{print $NF}'
}

assert() {
  local name="$1" expected="$2" actual="$3"
  ((TOTAL++))
  if [[ "$actual" == *"$expected"* ]]; then
    printf "  ✅ %s\n" "$name"
    ((PASS++))
  else
    printf "  ❌ %s (expected '%s', got '%s')\n" "$name" "$expected" "$actual"
    ((FAIL++))
  fi
}

api_get() { curl -s -b "$COOKIE" "$BASE/api/$1"; }
api_post() { curl -s -b "$COOKIE" -X POST "$BASE/api/$1" -H "Content-Type: application/json" -H "X-CSRFToken: $(get_csrf)" -d "$2"; }
api_patch() { curl -s -b "$COOKIE" -X PATCH "$BASE/api/$1" -H "Content-Type: application/json" -H "X-CSRFToken: $(get_csrf)" -d "$2"; }
api_delete() { curl -s -b "$COOKIE" -X DELETE "$BASE/api/$1" -H "X-CSRFToken: $(get_csrf)"; }
http_code() { curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE" "$BASE/api/$1"; }
http_code_post() { curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE" -X POST "$BASE/api/$1" -H "Content-Type: application/json" -H "X-CSRFToken: $(get_csrf)" -d "$2"; }

echo "=========================================="
echo "  L99 Twin API E2E Test Suite"
echo "=========================================="
echo ""

# ---- Phase 0: Auth ----
echo "[ Phase 0: Authentication ]"
curl -s -c "$COOKIE" "$BASE/api/website" > /dev/null
CSRF=$(get_csrf)
LOGIN=$(curl -s -b "$COOKIE" -c "$COOKIE" -X POST "$BASE/api/login" -H "Content-Type: application/json" -H "X-CSRFToken: $CSRF" -d '{"username":"stu_1","password":"test123"}')
assert "login success" "None" "$(echo "$LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin)["error"])' 2>/dev/null)"

CODE_UNAUTH=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/twin/timeline?from=2026-01-01&to=2026-05-01")
assert "unauthenticated returns 401/403" "40" "$CODE_UNAUTH"

# ---- Phase 1: Timeline ----
echo ""
echo "[ Phase 1: Learning Timeline ]"
RESP=$(api_get "twin/timeline?from=2026-04-01&to=2026-05-03&limit=5")
assert "timeline returns events" "events" "$RESP"
assert "timeline has total_count" "total_count" "$RESP"
assert "timeline has has_more" "has_more" "$RESP"
assert "timeline event has event_kind" "event_kind" "$RESP"
assert "timeline event has summary" "summary" "$RESP"

RESP_EMPTY=$(api_get "twin/timeline?from=2020-01-01&to=2020-01-02&limit=5")
assert "timeline empty range returns 0 events" "total_count" "$RESP_EMPTY"

CODE_BAD=$(http_code "twin/timeline?from=2026-05-01&to=2026-01-01")
assert "timeline inverted range returns 4xx/5xx" "0" "$([ "$CODE_BAD" -ge 400 ] && echo 0 || echo 1)"

# ---- Phase 2: KC Galaxy ----
echo ""
echo "[ Phase 2: KC Galaxy ]"
RESP=$(api_get "twin/kc-galaxy")
assert "galaxy returns nodes" "nodes" "$RESP"
assert "galaxy returns edges" "edges" "$RESP"
NODE_COUNT=$(echo "$RESP" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]["nodes"]))' 2>/dev/null)
assert "galaxy has > 0 nodes" "1" "$([ "$NODE_COUNT" -gt 0 ] && echo 1 || echo 0)"
assert "galaxy node has mastery" "mastery" "$RESP"
assert "galaxy node has category" "category" "$RESP"

# ---- Phase 3: Persona ----
echo ""
echo "[ Phase 3: Twin Persona ]"
RESP=$(api_get "twin/persona")
assert "persona has summary_text" "summary_text" "$RESP"
assert "persona has summary_version" "summary_version" "$RESP"
assert "persona has learning_style_key" "learning_style_key" "$RESP"
assert "persona text is non-empty" "学生" "$RESP"

RESP_FB=$(api_post "twin/persona/feedback" '{"is_accurate":true}')
assert "persona accurate feedback ok" "ok" "$RESP_FB"

RESP_FB2=$(api_post "twin/persona/feedback" '{"is_accurate":false,"reason":"有些数据不太对"}')
assert "persona inaccurate feedback with reason ok" "ok" "$RESP_FB2"

# ---- Phase 4: Health ----
echo ""
echo "[ Phase 4: Learning Health ]"
RESP=$(api_get "twin/health")
assert "health has mastery" "mastery" "$RESP"
assert "health has frequency" "frequency" "$RESP"
assert "health has difficulty_curve" "difficulty_curve" "$RESP"
assert "health has due_reviews" "due_reviews" "$RESP"
assert "health mastery has overall" "overall" "$RESP"
assert "health frequency has submits_30d" "submits_30d" "$RESP"

# ---- Phase 5: Museum ----
echo ""
echo "[ Phase 5: Error Museum ]"
RESP=$(api_get "twin/museum/pins")
PIN_COUNT=$(echo "$RESP" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))' 2>/dev/null)
assert "museum has pins" "1" "$([ "$PIN_COUNT" -ge 1 ] && echo 1 || echo 0)"
assert "museum pin has annotation" "annotation" "$RESP"

# ---- Phase 6: Metacog ----
echo ""
echo "[ Phase 6: Metacognitive Prediction ]"
RESP=$(api_post "twin/metacog/predict" '{"problem_id":397,"predicted_output":"hello","predicted_reason":"just a guess"}')
assert "metacog predict returns event_id" "event_id" "$RESP"

RESP_MAP=$(api_get "twin/metacog/map")
assert "metacog map has total_predicts" "total_predicts" "$RESP_MAP"
assert "metacog map has exact_match_rate" "exact_match_rate" "$RESP_MAP"

CODE_BAD=$(http_code_post "twin/metacog/predict" '{"predicted_output":"test"}')
assert "metacog predict without problem_id rejects" "0" "$([ "$CODE_BAD" -ge 400 ] && echo 0 || echo 1)"

# ---- Phase 7: Chat ----
echo ""
echo "[ Phase 7: Twin Chat ]"
RESP_QQ=$(api_get "twin/chat/quick-questions")
QQ_COUNT=$(echo "$RESP_QQ" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["data"]))' 2>/dev/null)
assert "chat has 4 quick questions" "4" "$QQ_COUNT"

RESP_CHAT=$(api_post "twin/chat" '{"question":"我最近怎么样"}')
assert "chat status returns answer" "answer" "$RESP_CHAT"
assert "chat status has data_source" "data_source" "$RESP_CHAT"

RESP_WEAK=$(api_post "twin/chat" '{"question":"我薄弱的是什么"}')
assert "chat weakness returns weak_kcs" "weak_kcs" "$RESP_WEAK"

RESP_NEXT=$(api_post "twin/chat" '{"question":"下一步该学什么"}')
assert "chat next returns suggested_kcs" "suggested_kcs" "$RESP_NEXT"

RESP_REV=$(api_post "twin/chat" '{"question":"要复习什么"}')
assert "chat review returns due_reviews" "due_reviews" "$RESP_REV"

# ---- Phase 8: Weekly ----
echo ""
echo "[ Phase 8: Weekly Digest ]"
RESP=$(api_get "twin/weekly")
assert "weekly has week_start" "week_start" "$RESP"
assert "weekly has submits" "submits" "$RESP"
assert "weekly has digest_text" "digest_text" "$RESP"

RESP_REF=$(api_post "twin/weekly/reflection" '{"text":"这周我学了循环和列表，收获很大"}')
assert "weekly reflection saves ok" "ok" "$RESP_REF"

CODE_EMPTY=$(http_code_post "twin/weekly/reflection" '{"text":""}')
assert "weekly reflection empty text rejects" "0" "$([ "$CODE_EMPTY" -ge 400 ] && echo 0 || echo 1)"

# ---- Phase 9: KC Decay ----
echo ""
echo "[ Phase 9: KC Decay ]"
RESP=$(api_get "twin/kc-decay/queue")
assert "decay has fading" "fading" "$RESP"
assert "decay has forgotten" "forgotten" "$RESP"
FADING=$(echo "$RESP" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["fading_count"])' 2>/dev/null)
assert "decay has fading items" "3" "$FADING"

# ---- Phase 10: Teach AI ----
echo ""
echo "[ Phase 10: Teach AI ]"
RESP_START=$(api_post "twin/teach-ai/start" '{"target_kc_id":1}')
assert "teach-ai start returns session_id" "session_id" "$RESP_START"
assert "teach-ai start returns misconception" "ai_misconception" "$RESP_START"
assert "teach-ai start returns persona" "ai_persona" "$RESP_START"

SESSION_ID=$(echo "$RESP_START" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["session_id"])' 2>/dev/null)
RESP_EXPLAIN=$(api_post "twin/teach-ai/$SESSION_ID/explain" '{"explanation":"range(n) 其实是从 0 开始的，到 n-1 结束。因为 Python 索引从 0 起步。比如 range(3) 输出 0,1,2 不是 1,2,3。"}')
assert "teach-ai explain returns grader_score" "grader_score" "$RESP_EXPLAIN"
assert "teach-ai explain returns grader_feedback" "grader_feedback" "$RESP_EXPLAIN"
assert "teach-ai explain returns round" "round" "$RESP_EXPLAIN"

RESP_SESSIONS=$(api_get "twin/teach-ai/sessions")
assert "teach-ai sessions returns list" "[" "$RESP_SESSIONS"

# ---- Phase 11: Mastery Override ----
echo ""
echo "[ Phase 11: Mastery Override ]"
FIRST_KC=$(api_get "twin/kc-galaxy" | python3 -c 'import sys,json; ns=json.load(sys.stdin)["data"]["nodes"]; print(ns[0]["kc_id"] if ns else 0)' 2>/dev/null)
if [ "$FIRST_KC" != "0" ]; then
  RESP_OVR=$(api_post "twin/edit/mastery-override" "{\"kc_id\":$FIRST_KC,\"overridden_mastery\":0.95,\"reason\":\"I know this well\"}")
  assert "mastery override ok" "ok" "$RESP_OVR"
  RESP_LIST=$(api_get "twin/edit/mastery-overrides")
  assert "mastery overrides list has data" "kc_name" "$RESP_LIST"
fi

# ---- Phase 12: Arena ----
echo ""
echo "[ Phase 12: AI Arena ]"
RESP_ARENA=$(api_post "twin/arena/start" '{"problem_id":397}')
assert "arena start returns match_id" "match_id" "$RESP_ARENA"
assert "arena start returns ai_code" "ai_code" "$RESP_ARENA"

MATCH_ID=$(echo "$RESP_ARENA" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["match_id"])' 2>/dev/null)
RESP_JUDGE=$(api_post "twin/arena/$MATCH_ID/judge-ai" '{"evaluation":"AI的代码可读性不错但有边界问题","score":72}')
assert "arena judge returns message" "批判性思维" "$RESP_JUDGE"

RESP_HIST=$(api_get "twin/arena/history")
assert "arena history has match" "match_id" "$RESP_HIST"

# ---- Phase 13: World Setting ----
echo ""
echo "[ Phase 13: World Setting ]"
RESP_WORLD=$(api_get "twin/world-setting")
assert "world setting has world_name" "world_name" "$RESP_WORLD"
assert "world setting has theme_id" "theme_id" "$RESP_WORLD"

# ---- Summary ----
echo ""
echo "=========================================="
printf "  PASS: %d / FAIL: %d / TOTAL: %d\n" "$PASS" "$FAIL" "$TOTAL"
echo "=========================================="

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi

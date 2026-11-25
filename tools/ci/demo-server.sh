#!/usr/bin/env bash
# Демо-сервер сайта для CI (T7): выдуманные данные, без интернета и без ключа доступа — как qa/helpers/server.js
# в репозитории сайта. Всё, что сервер пишет (события, история цен, обратная связь), — во временную папку.
#   bash tools/ci/demo-server.sh <папка сайта> [порт]     лог — в $CC_OUT/demo-server-<порт>.log (по умолчанию ci-out/)
# Эмулятор видит компьютер как 10.0.2.2: debug-сборка приложения открывает http://10.0.2.2:3000/.
set -euo pipefail
WEB=$(cd "${1:?папка сайта}" && pwd)
PORT=${2:-3000}
OUT=${CC_OUT:-$PWD/ci-out}
mkdir -p "$OUT"
TMP=$(mktemp -d)
LOG="$OUT/demo-server-$PORT.log"

# Магазины рядом (W81) — из сохранённого ответа OpenStreetMap, с отметкой «сейчас» (как osmFixture в qa/helpers/server.js)
OSM=""
if [ -f "$WEB/qa/fixtures/osm-belgrano.json" ]; then
  node -e 'const fs=require("fs");const fx=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));const o={};
    for(const[k,v]of Object.entries(fx))if(k.startsWith("osm2:"))o[k]={ts:Date.now(),data:v.data};
    fs.writeFileSync(process.argv[2],JSON.stringify(o));' "$WEB/qa/fixtures/osm-belgrano.json" "$TMP/osm.json"
  OSM="$TMP/osm.json"
fi

cd "$WEB"
env PORT="$PORT" HOST=127.0.0.1 PREWARM=0 PUBLIC= ACCESS_KEY= METRICS_KEY= RATE_LIMIT= CLIENT_REQUIRED= \
  OSM_CACHE_FILE="$OSM" EVENTS_FILE="$TMP/events.jsonl" EVENTS_DIR= TEAM_IDS= EVENTS_MAX_DAY_MB= \
  OFFERS_CHANGES_FILE="$TMP/offer-changes.jsonl" HISTORY_DB="$TMP/history.sqlite" PRODUCT_MAP_FILE="$TMP/product-map.sqlite" \
  PROMO_ARCHIVE_DIR="$TMP/promos" LEGAL_DIR="$TMP/legales" INGEST_LOCK="$TMP/ingest.lock" \
  FEEDBACK_DIR="$TMP/feedback" FEEDBACK_MAX_DAY_MB= \
  setsid nohup node --no-warnings server.js --demo >"$LOG" 2>&1 </dev/null &

for _ in $(seq 1 120); do
  if curl -sf -o /dev/null "http://127.0.0.1:$PORT/healthz"; then
    echo "Демо-сервер сайта: http://127.0.0.1:$PORT ($(git -C "$WEB" rev-parse --short HEAD 2>/dev/null || echo '?'))"
    exit 0
  fi
  sleep 0.25
done
echo "Демо-сервер не ответил за 30 с:"
tail -n 40 "$LOG"
exit 1

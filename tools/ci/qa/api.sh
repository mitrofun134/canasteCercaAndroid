#!/usr/bin/env bash
# Тест-кейсы QA P0/P1 с «Как» = API или DATA — автотестами qa/api и qa/data сайта, без эмулятора (T7).
# Серверы тесты поднимают сами (qa/helpers/server.js). Тесты на базе дня (snapshot) в CI пропускаются: базы тут нет.
#   bash tools/ci/qa/api.sh <папка сайта>        итог — в $CC_OUT (по умолчанию ci-out/)
set -uo pipefail
WEB=$(cd "${1:?папка сайта}" && pwd)
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${CC_OUT:-$PWD/ci-out}
mkdir -p "$OUT"
PATTERN=$(CC_OUT="$OUT" node "$HERE/cases.js" "$WEB/qa/TEST-CASES.md" api)
(
  cd "$WEB" &&
    node --test --test-timeout=120000 --test-name-pattern="$PATTERN" \
      --test-reporter=spec --test-reporter-destination=stdout \
      --test-reporter=junit --test-reporter-destination="$OUT/qa-api.xml" \
      qa/api/*.test.js qa/data/*.test.js
) 2>&1 | tee "$OUT/qa-api.txt"
code=${PIPESTATUS[0]}
node "$HERE/report.js" "$OUT/qa-api.xml" "QA P0/P1 — API и данные" "$OUT/qa-cases-api.txt"
exit "$code"

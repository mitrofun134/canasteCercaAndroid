#!/usr/bin/env bash
# Тест-кейсы QA P0/P1 с «Как» = UI или e2e — автотестами qa/e2e сайта, но внутри приложения на эмуляторе (T7).
# Внутри reactivecircus/android-emulator-runner. Перед этим (workflow): адрес 10.0.2.2 на lo машины CI и демо-сервер
# на 0.0.0.0:3000 — так http://10.0.2.2:3000 открывается и в приложении на эмуляторе, и у самих тестов.
#   CC_OUT, CC_WEB, CC_APK_DIR, CC_DENSITY, CC_NAME — как у tools/ci/emulator-run.sh
set -uo pipefail
OUT=${CC_OUT:?}
WEB=${CC_WEB:?}
APKS=${CC_APK_DIR:?}
HERE=$(cd "$(dirname "$0")" && pwd)
PKG=ar.canastacerca
mkdir -p "$OUT/video"

adb wait-for-device
# Окна «… isn't responding» на медленном эмуляторе забирают касания и «назад» у тестов — не показывать
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
if [ -n "${CC_DENSITY:-}" ]; then adb shell wm density "$CC_DENSITY"; fi
WV=$(adb shell dumpsys webviewupdate 2>/dev/null | tr -d '\r' | grep -m1 -i "current webview package" | sed 's/.*: *//')
echo "Android $(adb shell getprop ro.build.version.release | tr -d '\r'), WebView ${WV:-?}, экран $(adb shell wm size | tr -d '\r' | sed 's/.*: //')"
if ! adb install -r -g "$(find "$APKS" -name app-debug.apk | head -n 1)"; then
  echo "::error title=${CC_NAME:-QA в приложении}::приложение не установилось"
  exit 1
fi

adb logcat -c || true
adb logcat -v threadtime >"$OUT/logcat.txt" 2>&1 &
LOGCAT=$!
touch "$OUT/.rec"
(
  n=1
  while [ -f "$OUT/.rec" ]; do
    adb shell screenrecord --bit-rate 4000000 --time-limit 170 "/sdcard/Download/qa-rec-$n.mp4" >/dev/null 2>&1 || sleep 2
    n=$((n + 1))
  done
) &
REC=$!

PATTERN=$(CC_OUT="$OUT" node "$HERE/cases.js" "$WEB/qa/TEST-CASES.md" ui)
(cd "$WEB" && npm i --no-save --no-audit --no-fund playwright >/dev/null 2>&1) || echo "::warning::npm i playwright не прошёл"

# Дизайн (D96): снимки и замер экранов на двух ширинах — 412 dp (плотность 420) и 360 dp (480), прогон не роняет
for d in 420:412 480:360; do
  adb shell wm density "${d%%:*}" >/dev/null
  sleep 2
  echo "== Дизайн: ${d##*:} dp"
  CC_NAME="Android 14, ${d##*:} dp" CC_DESIGN_DIR="$OUT/design/android14-${d##*:}" node "$HERE/../design/shots.js" || true
done
if [ -n "${CC_DENSITY:-}" ]; then adb shell wm density "$CC_DENSITY" >/dev/null; fi
(
  cd "$WEB" &&
    CC_WEB="$WEB" QA_ANDROID=1 QA_ANDROID_PKG=$PKG QA_BASE=http://10.0.2.2:3000 \
      NODE_OPTIONS="--require=$HERE/android-preload.js" \
      timeout 960 node --test --test-concurrency=1 --test-timeout=600000 --test-name-pattern="$PATTERN" \
      --test-reporter=spec --test-reporter-destination=stdout \
      --test-reporter=junit --test-reporter-destination="$OUT/qa-ui.xml" \
      qa/e2e/*.test.js
) 2>&1 | tee "$OUT/qa-ui.txt"
code=${PIPESTATUS[0]}
node "$HERE/report.js" "$OUT/qa-ui.xml" "${CC_NAME:-QA в приложении}" "$OUT/qa-cases-ui.txt"

rm -f "$OUT/.rec"
adb shell 'for p in $(pidof screenrecord); do kill -2 $p; done' >/dev/null 2>&1
sleep 3
kill "$REC" "$LOGCAT" >/dev/null 2>&1
for f in $(adb shell ls /sdcard/Download/ 2>/dev/null | tr -d '\r' | grep '^qa-rec-.*\.mp4$'); do
  adb pull "/sdcard/Download/$f" "$OUT/video/" >/dev/null 2>&1
done
[ "$code" = 124 ] && echo "::error title=${CC_NAME:-QA в приложении}::не уложились в 16 минут — прогон оборван"
exit "$code"

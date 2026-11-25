#!/usr/bin/env bash
# Прогон на эмуляторе (T7) — внутри reactivecircus/android-emulator-runner: эмулятор загружен, adb его видит,
# демо-сервер сайта уже отвечает на 127.0.0.1:3000 (tools/ci/demo-server.sh).
#   CC_OUT      — папка артефактов (снимки, видео, logcat, вывод тестов)
#   CC_APK_DIR  — где лежат app-debug.apk и app-debug-androidTest.apk
#   CC_DENSITY  — плотность экрана (wm density): 480 → 360 dp в ширину, 420 → 412 dp (экран 1080 × 2400)
#   CC_WEB      — репозиторий сайта: смоук QA из qa/android (Q124), если он там уже есть
#   CC_BREAK    — true: намеренно красный прогон (приёмка T7)
# Ключей и токенов здесь нет и в лог они не печатаются: логи Actions публичные.
set -uo pipefail
OUT=${CC_OUT:?}
APKS=${CC_APK_DIR:?}
PKG=ar.canastacerca
DEV_SHOTS=/sdcard/Download/cc-ci
mkdir -p "$OUT/shots" "$OUT/video"
fail=0

# Итог проверок — в лог, ошибки — аннотациями (их видно на странице прогона и в PR без входа в GitHub),
# таблица — в сводку прогона. $1 — заголовок, $2 — файл «имя<TAB>итог<TAB>ошибка»
prop() { local v=${1//%/%25}; v=${v//,/%2C}; v=${v//:/%3A}; printf '%s' "$v"; }
gherr() { local m=${2//%/%25}; echo "::error title=$(prop "$1")::${m:-без сообщения}"; }
report() {
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '### %s — %s\n\n| Проверка | Итог | Ошибка |\n|---|---|---|\n' "${CC_NAME:-эмулятор}" "$1" >>"$GITHUB_STEP_SUMMARY"
  while IFS=$'\t' read -r name st msg; do
    printf '%-34s %s\n' "$name" "$st"
    if [ "$st" = "УПАЛА" ]; then gherr "${CC_NAME:-Эмулятор} · $name" "$msg"; fi
    [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "| $name | $st | ${msg//|/\\|} |" >>"$GITHUB_STEP_SUMMARY"
  done <"$2"
  return 0
}

adb wait-for-device
if [ -n "${CC_DENSITY:-}" ]; then adb shell wm density "$CC_DENSITY"; fi
adb shell settings put system font_scale 1.0
{
  echo "== экран"; adb shell wm size; adb shell wm density
  echo "== Android"; adb shell getprop ro.build.version.release; adb shell getprop ro.build.version.sdk
  echo "== WebView"; adb shell dumpsys webviewupdate | grep -iE "current webview package|version" | head -5
} | tee "$OUT/device.txt"

# logcat — весь прогон; видео — кусками по 170 с (screenrecord пишет не больше 3 минут за раз)
adb logcat -c || true
adb logcat -v threadtime >"$OUT/logcat.txt" 2>&1 &
LOGCAT=$!
adb shell rm -rf "$DEV_SHOTS" /sdcard/Download/cc-rec-*.mp4 >/dev/null 2>&1
adb shell mkdir -p "$DEV_SHOTS"
touch "$OUT/.rec"
(
  n=1
  while [ -f "$OUT/.rec" ]; do
    adb shell screenrecord --bit-rate 4000000 --time-limit 170 "/sdcard/Download/cc-rec-$n.mp4" >/dev/null 2>&1 || sleep 2
    n=$((n + 1))
  done
) &
REC=$!

# Приложение и тест — с выданными разрешениями (камера, геолокация): без системных окон поверх сайта
APK=$(find "$APKS" -name app-debug.apk | head -n 1)
TEST_APK=$(find "$APKS" -name app-debug-androidTest.apk | head -n 1)
adb install -r -g "$APK" && adb install -r -g "$TEST_APK" || fail=1

if [ "$fail" = 0 ]; then
  echo "== Тест оболочки (app/src/androidTest)"
  ARGS=()
  if [ "${CC_BREAK:-}" = "true" ]; then ARGS=(-e ccBreak 1); fi
  adb shell am instrument -w -r "${ARGS[@]}" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' >"$OUT/shell-test.txt"
  # Каждая проверка: имя, итог, первая строка ошибки (stack=…)
  awk '/^INSTRUMENTATION_STATUS: test=/{t=substr($0,30)} /^INSTRUMENTATION_STATUS: stack=/{m=substr($0,31)}
    /^INSTRUMENTATION_STATUS_CODE: /{c=$2; if(t!=""&&c!=1){s=(c==0?"ok":(c==-3||c==-4)?"пропущена":"УПАЛА");
    print t "\t" s "\t" (s=="УПАЛА"?m:""); t=""; m=""}}' "$OUT/shell-test.txt" >"$OUT/shell-test.tsv"
  report "Тест оболочки" "$OUT/shell-test.tsv"
  grep -E "^(OK \(|FAILURES!!!|Tests run:)" "$OUT/shell-test.txt" || true
  if ! grep -q "^OK (" "$OUT/shell-test.txt"; then
    fail=1
    # Тест не дошёл до конца (упал процесс, нет эмулятора) — тоже видно без входа в GitHub
    if ! grep -q "УПАЛА" "$OUT/shell-test.tsv"; then
      gherr "${CC_NAME:-Эмулятор} · тест оболочки не закончился" "$(grep -m1 -E 'INSTRUMENTATION_(RESULT|FAILED)|shortMsg|longMsg|Error' "$OUT/shell-test.txt" | cut -c1-300)"
    fi
    echo "== Что упало"
    awk '/^INSTRUMENTATION_STATUS: stack=/{p=1; print; next} /^INSTRUMENTATION_(STATUS|RESULT|CODE)/{p=0} p' "$OUT/shell-test.txt" | head -n 80
  fi
else
  gherr "${CC_NAME:-Эмулятор} · приложение не установилось" "adb install — см. лог шага"
fi

if [ -d "${CC_WEB:-}/qa/android" ] && ls "$CC_WEB"/qa/android/*.test.js >/dev/null 2>&1; then
  echo "== Смоук QA (qa/android, Q124)"
  mkdir -p "$OUT/qa"
  (
    cd "$CC_WEB" &&
      npm i --no-save --no-audit --no-fund playwright >/dev/null &&
      QA_ANDROID=1 QA_ANDROID_PKG=$PKG QA_BASE=http://127.0.0.1:3000 QA_ANDROID_OUT="$OUT/qa" \
        node --test --test-reporter=spec qa/android/*.test.js
  ) 2>&1 | tee "$OUT/qa-smoke.txt"
  [ "${PIPESTATUS[0]}" = 0 ] || fail=1
else
  echo "== Смоук QA: qa/android/*.test.js в репозитории сайта ещё нет (Q124) — пропуск"
fi

# Видео: screenrecord дописывает файл по SIGINT
rm -f "$OUT/.rec"
adb shell 'for p in $(pidof screenrecord); do kill -2 $p; done' >/dev/null 2>&1
sleep 3
kill "$REC" >/dev/null 2>&1
for f in $(adb shell ls /sdcard/Download/ 2>/dev/null | tr -d '\r' | grep '^cc-rec-.*\.mp4$'); do
  adb pull "/sdcard/Download/$f" "$OUT/video/" >/dev/null 2>&1
done
adb pull "$DEV_SHOTS/." "$OUT/shots/" >/dev/null 2>&1
kill "$LOGCAT" >/dev/null 2>&1
echo "== Артефакты: $(ls "$OUT/shots" | wc -l) снимков, $(ls "$OUT/video" | wc -l) видео, logcat $(du -h "$OUT/logcat.txt" | cut -f1)"
exit "$fail"

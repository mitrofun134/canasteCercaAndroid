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
# Минимальный WebView для сайта — как BuildConfig.MIN_WEBVIEW в app/build.gradle.kts (ГД 25.11): ниже приложение
# показывает «Actualizá Android System WebView», и проверки сайта пропускаются
MIN_WV=$(sed -n 's/.*"MIN_WEBVIEW", "\([0-9]*\)".*/\1/p' "$(dirname "$0")/../../app/build.gradle.kts")
MIN_WV=${MIN_WV:-105}
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
# Окна «… isn't responding» на медленном эмуляторе забирают касания и «назад» у тестов — не показывать
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
if [ -n "${CC_DENSITY:-}" ]; then adb shell wm density "$CC_DENSITY"; fi
adb shell settings put system font_scale 1.0
{
  echo "== экран"; adb shell wm size; adb shell wm density
  echo "== Android"; adb shell getprop ro.build.version.release; adb shell getprop ro.build.version.sdk
  echo "== WebView"; adb shell dumpsys webviewupdate | grep -iE "current webview package|version" | head -5
} | tee "$OUT/device.txt"
# Версия Android и WebView — отметкой на странице прогона (видно без входа в GitHub)
WV=$(adb shell dumpsys webviewupdate 2>/dev/null | tr -d '\r' | grep -m1 -i "current webview package" | sed 's/.*: *//')
WV_MAJOR=$(echo "$WV" | sed -n 's/.*, *\([0-9]*\)\..*/\1/p')
if [ -n "$WV_MAJOR" ] && [ "$WV_MAJOR" -lt "$MIN_WV" ]; then
  echo "::notice title=$(prop "${CC_NAME:-Эмулятор}") · старый WebView::WebView $WV_MAJOR (сайту нужен $MIN_WV+): приложение показывает экран «Actualizá Android System WebView» — его и проверяем (t09); проверки сайта пропускаются"
fi
echo "::notice title=$(prop "${CC_NAME:-Эмулятор}")::Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), WebView ${WV:-?}"

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
  adb shell am instrument -w -r -e class "$PKG.ShellTest" "${ARGS[@]}" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' >"$OUT/shell-test.txt"
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

# Дизайн (D96): снимки и замер 12 экранов — DesignTest, на двух ширинах (плотность 420 → 412 dp, 480 → 360 dp).
# Только на WebView $MIN_WV+ (на старом приложение сайт не открывает). Прогон не роняет
if [ "$fail" = 0 ] || [ -s "$OUT/shell-test.txt" ]; then
  if [ "${WV_MAJOR:-0}" -ge "$MIN_WV" ]; then
    for d in 420:412 480:360; do
      dp=${d##*:}
      adb shell wm density "${d%%:*}" >/dev/null
      adb shell rm -rf /sdcard/Download/cc-design >/dev/null 2>&1
      sleep 2
      echo "== Дизайн: ${dp} dp"
      adb shell am instrument -w -r -e class "$PKG.DesignTest" -e ccDesign "$dp" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" |
        tr -d '\r' >"$OUT/design-$dp.txt"
      mkdir -p "$OUT/design/android-$dp"
      adb pull /sdcard/Download/cc-design/. "$OUT/design/android-$dp/" >/dev/null 2>&1
      node "$(dirname "$0")/design/report.js" "$OUT/design-$dp.txt" "$OUT/design/android-$dp" "${CC_NAME%%,*}, $dp dp" || true
      # Не дошёл до конца — почему: итог instrument и что было в logcat (падение, ANR, процесс сайта)
      if ! grep -q "^OK (" "$OUT/design-$dp.txt"; then
        why=$( { grep -m3 -E "shortMsg|longMsg|Caused by|Exception" "$OUT/design-$dp.txt";
          grep -E "FATAL EXCEPTION|ANR in|has died|lowmemorykiller|onRenderProcessGone|Canasta|CanastaCI|chromium.*(crash|OOM)" "$OUT/logcat.txt" | tail -n 8; } | cut -c1-240 | tr '\n' '\001' | sed 's/%/%25/g; s/\x01/%0A/g; s/::/: /g')
        echo "::warning title=$(prop "Дизайн · ${dp} dp · обрыв")::${why:-причина не найдена — design-$dp.txt и logcat.txt в артефакте}"
      fi
    done
    if [ -n "${CC_DENSITY:-}" ]; then adb shell wm density "$CC_DENSITY" >/dev/null; fi
  else
    echo "== Дизайн: пропуск — WebView ${WV_MAJOR:-?}, сайту нужен $MIN_WV+"
  fi
fi

if [ "${WV_MAJOR:-0}" -ge "$MIN_WV" ] && [ -d "${CC_WEB:-}/qa/android" ] && ls "$CC_WEB"/qa/android/*.test.js >/dev/null 2>&1; then
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

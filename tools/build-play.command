#!/bin/bash
# Сборка для Google Play одним кликом (двойной клик в Finder или: bash tools/build-play.command).
#   1) собирает AAB для Play и APK для установки на телефон напрямую — оба подписаны ключом загрузки
#      (~/.gradle/gradle.properties → canasta.upload.*, README → «Сборка для Google Play»);
#   2) пишет весь вывод в build/build-play.log — его читает и чат приложений;
#   3) показывает AAB в Finder.
# Сайт — адрес и ключ из ~/.gradle/gradle.properties (по умолчанию https://canastacerca.com/).
set -u
cd "$(dirname "$0")/.." || exit 1
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[ -d "$JBR" ] && export JAVA_HOME="$JBR"
mkdir -p build
LOG="build/build-play.log"
AAB="app/build/outputs/bundle/release/app-release.aab"
APK="app/build/outputs/apk/release/app-release.apk"
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
if [ -f app/google-services.json ]; then
  FB="Crashlytics: есть (app/google-services.json) — перед загрузкой в Play обновить анкету «Безопасность данных»"
else
  FB="Crashlytics: нет (app/google-services.json не найден) — Firebase в сборке нет"
fi

{
  echo "Сборка $VERSION ($CODE) — $(date '+%Y-%m-%d %H:%M:%S')"
  echo "$FB"
  ./gradlew bundleRelease assembleRelease --console=plain 2>&1
  echo "exit=$?"
} | tee "$LOG"

if grep -q '^exit=0$' "$LOG" && [ -f "$AAB" ]; then
  ls -la "$AAB" "$APK" | tee -a "$LOG"
  open -R "$AAB"
  echo
  echo "✓ Готово: $VERSION ($CODE). AAB — в Play Console («Внутреннее тестирование» → новый выпуск),"
  echo "  APK ($APK) — можно поставить на телефон напрямую. Лог — $LOG"
  echo "  $FB"
else
  echo
  echo "✗ Сборка не удалась — ошибка выше и в $LOG"
fi
read -r -p "Enter, чтобы закрыть окно"

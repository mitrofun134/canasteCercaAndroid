#!/bin/bash
# Пробная сборка: приложение открывает сайт прямо с этого Mac — с тем, что ещё не выложено на canastacerca.com.
# Двойной клик (или: bash tools/test-build.command). Что делает:
#   1) запускает туннель Cloudflare из папки сайта (scripts/share.js — как share.command), ждёт новую ссылку;
#   2) собирает APK на эту ссылку, подписывает ключом загрузки — ставится поверх приложения из Play;
#   3) показывает файл canasta-test.apk в Finder.
# Окно не закрывать, пока проверяете: закроете — туннель остановится и приложение перестанет открывать сайт.
# В Google Play этот APK не загружать: ссылка временная. Папка сайта — CANASTA_WEB (по умолчанию ~/Downloads/canasta-cerca).
set -u
cd "$(dirname "$0")/.." || exit 1
WEB="${CANASTA_WEB:-$HOME/Downloads/canasta-cerca}"
LINK="$WEB/.share-link.txt"
OUT="app/build/outputs/apk/release"
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[ -d "$JBR" ] && export JAVA_HOME="$JBR"

fail() { echo; echo "✗ $1"; read -r -p "Enter, чтобы закрыть окно"; exit 1; }
[ -f "$WEB/scripts/share.js" ] || fail "Не нашёл сайт в $WEB"
command -v node >/dev/null 2>&1 || fail "Нет Node.js: https://nodejs.org (LTS)"
command -v cloudflared >/dev/null 2>&1 || fail "Нет cloudflared: brew install cloudflared"

start=$(date +%s)
(cd "$WEB" && exec node scripts/share.js) &
SHARE=$!
trap 'kill $SHARE 2>/dev/null' EXIT

echo "Жду ссылку туннеля…"
fresh=""
for _ in $(seq 1 120); do
  if [ -f "$LINK" ] && [ "$(stat -f %m "$LINK")" -ge "$start" ]; then fresh=1; break; fi
  kill -0 $SHARE 2>/dev/null || fail "Туннель не запустился — сообщение выше"
  sleep 1
done
[ -n "$fresh" ] || fail "Ссылка не появилась за 2 минуты"
L=$(head -n 1 "$LINK")
BASE="${L%%\?k=*}"
KEY="${L##*\?k=}"
case "$BASE" in https://*.trycloudflare.com/) ;; *) fail "Странная ссылка: $BASE" ;; esac

echo
echo "Собираю APK на $BASE (1–3 минуты)…"
./gradlew -q assembleRelease -Pcanasta.baseUrl="$BASE" -Pcanasta.accessKey="$KEY" || fail "Сборка не удалась — ошибка выше"
cp "$OUT/app-release.apk" "$OUT/canasta-test.apk" || fail "Нет $OUT/app-release.apk"
open -R "$OUT/canasta-test.apk"

echo
echo "✓ Готово: canasta-test.apk (открыл в Finder). Перекиньте на телефон и установите поверх приложения."
echo "  Сайт — с этого Mac: $BASE"
echo "  Окно не закрывайте, пока проверяете. В Google Play этот файл не загружать."
wait $SHARE

#!/bin/bash
# Снимки и замер экранов для дизайнера из последнего прогона «Android — эмулятор» на main (T7) — в папку сайта:
# ~/Downloads/canasta-cerca/design/screens/ci/<дата>-run<номер>/design-android10-360, design-android14-412 (в git не идут).
# Двойной клик. Нужен GitHub CLI один раз: brew install gh && gh auth login. Номер прогона можно дать аргументом.
REPO=mitrofun134/canasteCercaAndroid
DEST="$HOME/Downloads/canasta-cerca/design/screens/ci"
if ! command -v gh >/dev/null; then
  echo "Нет GitHub CLI. Один раз в Терминале: brew install gh && gh auth login"
  read -r -p "Enter — закрыть"
  exit 1
fi
RUN=${1:-$(gh run list -R "$REPO" -w "Android — эмулятор" -b main -L 1 --json databaseId -q '.[0].databaseId')}
DIR="$DEST/$(date +%Y-%m-%d)-run$RUN"
mkdir -p "$DIR"
if gh run download "$RUN" -R "$REPO" -p 'design-*' -D "$DIR"; then
  echo "Готово: $DIR (index.html в каждой папке — снимки и замер на одной странице)"
  open "$DIR"
else
  echo "Не скачалось: прогон $RUN ещё идёт или в нём нет снимков дизайна"
fi
read -r -p "Enter — закрыть"

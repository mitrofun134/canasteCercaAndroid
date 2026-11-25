'use strict';
// Какие тест-кейсы QA гонять в CI (T7): строки qa/TEST-CASES.md сайта с приоритетом P0 или P1 и нужным «Как».
//   node tools/ci/qa/cases.js <qa/TEST-CASES.md> ui|api   → в stdout — регулярка для node --test --test-name-pattern
// ui — кейсы «UI» и «e2e» (сайт внутри приложения на эмуляторе), api — «API», «DATA», «данные» (без эмулятора).
// Имена автотестов QA начинаются с номеров кейсов: «NAV-01/02/06 …», «CP-01 …», «Q120-3, 6 …» — регулярка ловит и
// номер из такого списка. Список кейсов — в $CC_OUT/qa-cases-<вид>.txt (что выбрано и почему).
// Кейсы, которые QA исключил для приложения, — qa/android/skip.txt сайта: номер кейса в начале строки, дальше — почему.
const fs = require('node:fs');
const path = require('node:path');

const [file, kind = 'ui'] = process.argv.slice(2);
const how = kind === 'api' ? /\b(API|DATA)\b|данные/ : /\b(UI|e2e)\b/;
const skipFile = path.join(path.dirname(file), 'android', 'skip.txt');
const skip = new Set(
  fs.existsSync(skipFile) ? fs.readFileSync(skipFile, 'utf8').split('\n').map((l) => (l.match(/^\s*([A-Z][A-Z0-9]*-[0-9][0-9A-Za-z.]*)/) || [])[1]).filter(Boolean) : [],
);
const rows = [];
for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
  const m = line.match(/^\| ([A-Z][A-Z0-9]*-[0-9][0-9A-Za-z.]*) \|/);
  if (!m) continue;
  const cells = line.trim().replace(/^\||\|$/g, '').split('|').map((x) => x.trim());
  const pr = cells.find((x) => /^P[0-3]$/.test(x));
  const way = cells[cells.length - 1];
  if ((pr === 'P0' || pr === 'P1') && how.test(way) && !(kind === 'ui' && skip.has(m[1]))) rows.push({ id: m[1], pr, way, what: cells[1] });
}
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// «NAV-06» ловит и «NAV-06 …», и «NAV-01/02/06 …»
const alt = rows.map(({ id }) => {
  const [area, num] = [id.slice(0, id.indexOf('-')), id.slice(id.indexOf('-') + 1)];
  return `${esc(area)}-(?:[0-9A-Za-z.]+/)*${esc(num)}(?![0-9A-Za-z])`;
});
const out = process.env.CC_OUT;
if (out) {
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, `qa-cases-${kind}.txt`), rows.map((r) => `${r.id}\t${r.pr}\t${r.way}\t${r.what}`).join('\n') + '\n');
}
process.stderr.write(`Кейсы QA P0/P1 (${kind}): ${rows.length}${kind === 'ui' && skip.size ? `, исключены для приложения (qa/android/skip.txt): ${skip.size}` : ''}\n`);
process.stdout.write(alt.length ? alt.join('|') : 'ничего-не-выбрано');

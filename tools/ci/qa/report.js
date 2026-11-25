'use strict';
// Итог автотестов QA в CI (T7): JUnit от node --test → итог в лог, сводка прогона (таблица упавших) и отметки
// ::error / ::notice — их видно на странице прогона и в PR без входа в GitHub.
//   node tools/ci/qa/report.js <junit.xml> "<заголовок>" [qa-cases-<вид>.txt]
const fs = require('node:fs');

const [file, title = 'QA', casesFile] = process.argv.slice(2);
const xml = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
const unxml = (s) => String(s || '').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#10;/g, ' ').replace(/&amp;/g, '&');

const tests = [];
for (const m of xml.matchAll(/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
  const name = unxml((m[1].match(/\bname="([^"]*)"/) || [])[1]);
  const body = m[3] || '';
  const fail = body.match(/<failure\b[^>]*?message="([^"]*)"/);
  const skip = /<skipped\b/.test(body);
  tests.push({ name, st: fail ? 'fail' : skip ? 'skip' : 'ok', msg: fail ? unxml(fail[1]).replace(/\s+/g, ' ').slice(0, 300) : '' });
}
const n = (st) => tests.filter((t) => t.st === st).length;
const ok = n('ok');
const failed = tests.filter((t) => t.st === 'fail');
const skipped = n('skip');

// Кейсы P0/P1, для которых нет ни одного автотеста с этим номером в имени
let missing = [];
if (casesFile && fs.existsSync(casesFile)) {
  const names = tests.map((t) => t.name).join('\n');
  for (const line of fs.readFileSync(casesFile, 'utf8').split('\n').filter(Boolean)) {
    const [id, pr] = line.split('\t');
    const [area, num] = [id.slice(0, id.indexOf('-')), id.slice(id.indexOf('-') + 1)];
    const re = new RegExp(`${area}-(?:[0-9A-Za-z.]+/)*${num.replace(/\./g, '\\.')}(?![0-9A-Za-z])`);
    if (!re.test(names)) missing.push(`${id} (${pr})`);
  }
}

const line = `${title}: ${ok} прошли, ${failed.length} упали, ${skipped} пропущены${missing.length ? `; без автотеста в прогоне — ${missing.length} кейсов` : ''}`;
console.log(`\n== ${line}`);
for (const t of failed) console.log(`УПАЛ  ${t.name}\n      ${t.msg}`);

const prop = (s) => String(s).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A').replace(/:/g, '%3A').replace(/,/g, '%2C');
const msg = (s) => String(s).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
if (process.env.GITHUB_ACTIONS) {
  console.log(`::notice title=${prop(title)}::${msg(line)}`);
  for (const t of failed.slice(0, 8)) console.log(`::error title=${prop(`${title} · ${t.name.slice(0, 80)}`)}::${msg(t.msg || 'без сообщения')}`);
}
const sum = process.env.GITHUB_STEP_SUMMARY;
if (sum) {
  const cell = (s) => String(s).replace(/\|/g, '\\|');
  let md = `### ${title}\n\n${line}\n\n`;
  if (failed.length) md += `| Упал | Ошибка |\n|---|---|\n${failed.map((t) => `| ${cell(t.name)} | ${cell(t.msg)} |`).join('\n')}\n\n`;
  if (missing.length) md += `<details><summary>Кейсы P0/P1 без автотеста в этом прогоне (${missing.length})</summary>\n\n${missing.join(', ')}\n\n</details>\n\n`;
  fs.appendFileSync(sum, md);
}

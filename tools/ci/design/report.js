'use strict';
// Итог снимков для дизайнера (T7, D96): из вывода DesignTest (строки «INSTRUMENTATION_STATUS: design=<JSON>») —
// index.html со снимками и замером, measure.json, одна отметка ::notice по экранам (видна без входа в GitHub)
// и таблица в сводке прогона. Пороги — правила design/specs (judge). Ничего не роняет.
//   node tools/ci/design/report.js <вывод am instrument> <папка со снимками> "<название>"
const fs = require('node:fs');
const path = require('node:path');

const [file, OUT, NAME = 'эмулятор'] = process.argv.slice(2);

// Пороги — из design/specs (см. measure.js)
const judge = (m) => {
  const bad = [];
  if (m.sideScroll > 1) bad.push(`шире экрана на ${m.sideScroll} px`);
  if (m.small.length) bad.push(`текст < 12 px: ${m.small.length}`);
  if (m.sizes.length > 7) bad.push(`размеров шрифта ${m.sizes.length} (> 7)`);
  if (m.heavy.length) bad.push(`начертания 500/900: ${m.heavy.length}`);
  if (m.smallTargets.length) bad.push(`нажимаемое < 44 px: ${m.smallTargets.length}`);
  if (m.clipped.length) bad.push(`обрезано: ${m.clipped.length}`);
  if (m.left != null && m.left < 12) bad.push(`отступ слева ${m.left} px`);
  if (m.fixedTop > 40) bad.push(`закреплено ${m.fixedTop} % первого экрана (> 40)`);
  if (m.fixedAfter != null && m.fixedAfter > 18) bad.push(`закреплено ${m.fixedAfter} % после прокрутки (> 18)`);
  return bad;
};

function html(results) {
  const e = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const list = (title, xs) => (xs && xs.length ? `<p><b>${e(title)}:</b> ${xs.map(e).join('; ')}</p>` : '');
  const cards = results
    .map((r) => {
      const m = r.m || {};
      return `<section><h2>${e(r.what)} <code>${e(r.path)}</code></h2>
<div class="shots">${r.shots.map((f) => `<a href="${e(f)}"><img src="${e(f)}" alt="${e(r.what)}" loading="lazy"></a>`).join('')}</div>
<div class="m"><p class="${r.bad.length ? 'bad' : 'ok'}">${r.bad.length ? e(r.bad.join('; ')) : 'Замечаний по правилам нет'}</p>
<p>Ширина ${e(m.width)} px · экранов ${e(m.screens)} · размеры шрифта: ${e((m.sizes || []).join(', '))} · начертания: ${e((m.weights || []).join(', '))} · отступы ${e(m.left)} / ${e(m.right)} px · закреплено ${e(m.fixedTop)} % → ${e(m.fixedAfter)} %</p>
${list('Текст < 12 px', m.small)}${list('500/900', m.heavy)}${list('Нажимаемое < 44 px', m.smallTargets)}${list('Обрезано', m.clipped)}</div></section>`;
    })
    .join('\n');
  return `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Дизайн — ${e(NAME)}</title>
<style>body{font:14px/1.45 system-ui,sans-serif;margin:24px;color:#1a1a1a}h1{font-size:20px}h2{font-size:16px;margin:28px 0 8px}
code{color:#555}.shots{display:flex;gap:12px;flex-wrap:wrap}.shots img{height:560px;border:1px solid #ddd;border-radius:8px}
.m p{margin:6px 0;max-width:980px}.bad{color:#b00020;font-weight:600}.ok{color:#1b7f3b;font-weight:600}</style></head>
<body><h1>Дизайн — ${e(NAME)}</h1><p>Сайт внутри приложения на эмуляторе, демо-данные. Правила замера — design/specs: текст от 12 px,
не больше 7 размеров шрифта, без 500 и 900, нажимаемое от 44 px, без обрезанного текста, закреплённое ≤ 40 % первого экрана и ≤ 18 % после прокрутки.</p>
${cards}</body></html>`;
}


const results = [];
for (const line of (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '').split('\n')) {
  const m = line.match(/^INSTRUMENTATION_STATUS: design=(.*)$/);
  if (!m) continue;
  try {
    const r = JSON.parse(m[1]);
    r.shots = r.shots || [];
    r.bad = r.error ? [`не открылся: ${r.error}`] : judge(r.m || {});
    results.push(r);
  } catch {}
}
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'measure.json'), JSON.stringify({ name: NAME, at: new Date().toISOString(), screens: results }, null, 1));
fs.writeFileSync(path.join(OUT, 'index.html'), html(results));
const withIssues = results.filter((r) => r.bad.length).length;
const line = results.length
  ? `${NAME}: ${results.length} экранов, замечания по правилам design/specs — на ${withIssues}`
  : `${NAME}: снимков нет — DesignTest не дошёл до экранов (см. design-*.txt в артефакте)`;
console.log(`== ${line}`);
for (const r of results) console.log(`${String(r.path).padEnd(28)} ${r.bad.join('; ') || 'ок'}`);
const esc = (s) => String(s).replace(/%/g, '%25');
if (process.env.GITHUB_ACTIONS) {
  const lines = results.map((r) => {
    const m = r.m || {};
    const ex = [...(m.small || []).slice(0, 2), ...(m.smallTargets || []).slice(0, 2), ...(m.clipped || []).slice(0, 1)];
    return `${r.path} — ${r.bad.join('; ') || 'ок'}${ex.length ? ` (напр.: ${ex.join('; ')})` : ''}`;
  });
  const text = `${line}. Снимки и index.html — артефакт design-…\n${lines.join('\n')}`.slice(0, 6000);
  console.log(`::notice title=Дизайн · ${esc(NAME).replace(/,/g, '%2C').replace(/:/g, '%3A')}::${esc(text).replace(/\n/g, '%0A')}`);
}
if (process.env.GITHUB_STEP_SUMMARY) {
  const cell = (x) => String(x).replace(/\|/g, '\\|');
  let md = `### Дизайн — ${NAME}\n\n${line}.\n\n| Экран | Ширина | Размеры шрифта | Замечания |\n|---|---|---|---|\n`;
  for (const r of results) md += `| ${cell(r.what)} \`${r.path}\` | ${r.m ? r.m.width : '—'} | ${r.m && r.m.sizes ? r.m.sizes.join(', ') : '—'} | ${cell(r.bad.join('; ') || 'нет')} |\n`;
  fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, md + '\n');
}

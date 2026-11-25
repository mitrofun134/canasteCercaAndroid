'use strict';
// Снимки и замеры экранов для дизайнера (T7, ГД 25.11: «дизайнер говорит, всё ли ок по размерам, шрифтам,
// отображению на телефоне»). Сайт — внутри приложения на эмуляторе (Playwright для Android → WebView приложения),
// данные — демо-сервер CI. На каждый экран: снимок первого экрана и после прокрутки на экран вниз (как видит человек,
// со строкой состояния и меню телефона) и замер по правилам design/specs (measure.js). Результат — в $CC_OUT/design:
// PNG, measure.json и index.html (всё на одной странице); коротко — в сводке прогона. Прогон не роняет никогда.
//   CC_WEB — репозиторий сайта (там npm i playwright), CC_OUT, CC_NAME; adb видит эмулятор; приложение установлено.
const fs = require('node:fs');
const path = require('node:path');

const WEB = process.env.CC_WEB;
const OUT = path.join(process.env.CC_OUT || 'ci-out', 'design');
const NAME = process.env.CC_NAME || 'эмулятор';
const PKG = process.env.QA_ANDROID_PKG || 'ar.canastacerca';
const APP_BASE = 'http://10.0.2.2:3000'; // так сервер видит приложение (debug-сборка)
const NODE_BASE = process.env.CC_NODE_BASE || 'http://127.0.0.1:3000'; // так — этот скрипт
const STORAGE_KEY = 'canasta-cerca-v1';
// Адрес приёмки QA (qa/helpers/browser.js → CABA_LOC): для него у демо есть магазины рядом
const LOC = { lat: -34.553492488, lng: -58.453999699, label: 'Franklin D. Roosevelt 1700, Belgrano, Ciudad Autónoma de Buenos Aires', kind: 'address' };

const SCREENS = [
  { id: 'inicio', path: '/', what: 'Главная' },
  { id: 'ofertas-coto', path: '/ofertas/coto', what: 'Скидки сети' },
  { id: 'catalogo', path: '/catalogo', what: 'Каталог' },
  { id: 'catalogo-yerba', path: '/catalogo/yerba', what: 'Раздел каталога' },
  { id: 'buscar-leche', path: '/buscar?q=leche', what: 'Поиск «leche»' },
  { id: 'producto-banana', path: '/p/banana', what: 'Товар' },
  { id: 'canasta', path: '/canasta', what: 'Корзина и ответ', calc: true },
  { id: 'recetas', path: '/recetas', what: 'Рецепты' },
  { id: 'receta', path: '/recetas/milanesas-con-pure', what: 'Рецепт' },
  { id: 'descuentos', path: '/descuentos', what: 'Скидки дня' },
  { id: 'ahorro', path: '/ahorro', what: '«Mi ahorro»' },
  { id: 'pagos', path: '/cuenta/pagos', what: 'Способы оплаты' },
];

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

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const { _android } = require(require.resolve('playwright', { paths: [WEB || process.cwd()] }));
  const measure = require('./measure.js');
  const [device] = await _android.devices();
  if (!device) throw new Error('adb не видит эмулятор');
  const sh = async (cmd) => (await device.shell(cmd)).toString().trim();

  // Корзина для «Tu canasta»: по три товара yerba и leche из демо
  const basket = [];
  for (const cat of ['yerba', 'leche']) {
    const r = await fetch(`${NODE_BASE}/api/catalog?cat=${cat}`).then((x) => x.json()).catch(() => ({}));
    for (const x of (r.items || []).slice(0, 3)) basket.push({ id: x.id, name: x.name, brand: x.brand, presentation: x.presentation, qty: 1, scope: 'super', prices: null, offers: {}, pays: {} });
  }
  const state = { loc: LOC, lang: 'es', radiusKm: 2, onboardPay: 'done', basket };

  await sh(`am force-stop ${PKG}`);
  await sh(`pm clear ${PKG}`);
  for (const p of ['CAMERA', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION']) await sh(`pm grant ${PKG} android.permission.${p}`).catch(() => {});
  await sh(`am start -W -n ${PKG}/.MainActivity`);
  const wv = await device.webView({ pkg: PKG }, { timeout: 30000 });
  const page = await wv.page();
  await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
  await page.evaluate(([k, v]) => localStorage.setItem(k, v), [STORAGE_KEY, JSON.stringify(state)]);

  const results = [];
  for (const s of SCREENS) {
    const res = { ...s, shots: [], error: null };
    try {
      await page.goto(APP_BASE + s.path, { waitUntil: 'load', timeout: 30000 });
      if (s.calc) {
        const btn = page.locator('#calc-btn');
        if ((await btn.count()) && (await btn.isEnabled().catch(() => false))) await btn.click().catch(() => {});
        await page.waitForFunction(() => (document.querySelector('#results') || {}).innerText, null, { timeout: 20000 }).catch(() => {});
      }
      // Дорисовался: нет видимых заглушек и aria-busy (как SETTLED у приложения), но не дольше 8 с
      await page
        .waitForFunction(() => {
          for (const n of document.querySelectorAll('[class*="skel"],[aria-busy="true"]')) {
            const r = n.getBoundingClientRect();
            if (r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < innerHeight) return false;
          }
          return true;
        }, null, { timeout: 8000 })
        .catch(() => {});
      await page.waitForTimeout(700);
      const top = `${s.id}-1.png`;
      await device.screenshot({ path: path.join(OUT, top) });
      res.shots.push(top);
      res.m = await page.evaluate(measure);
      await page.evaluate(() => window.scrollTo(0, innerHeight * 0.9));
      await page.waitForTimeout(800);
      const second = `${s.id}-2.png`;
      await device.screenshot({ path: path.join(OUT, second) });
      res.shots.push(second);
      res.m.fixedAfter = await page.evaluate(() => {
        let h = 0;
        for (const el of document.querySelectorAll('body *')) {
          const cs = getComputedStyle(el);
          if (cs.position !== 'fixed' && cs.position !== 'sticky') continue;
          const r = el.getBoundingClientRect();
          if (r.width < innerWidth / 2 || cs.visibility === 'hidden' || cs.display === 'none') continue;
          const a = Math.max(0, r.top);
          const b = Math.min(innerHeight, r.bottom);
          if (b > a) h += b - a;
        }
        return Math.round((100 * h) / innerHeight);
      });
      res.bad = judge(res.m);
    } catch (e) {
      res.error = String(e.message || e).split('\n')[0];
      res.bad = [`не открылся: ${res.error}`];
    }
    results.push(res);
    console.log(`${s.id.padEnd(18)} ${res.bad.length ? res.bad.join('; ') : 'ok'}`);
  }
  await device.close();

  fs.writeFileSync(path.join(OUT, 'measure.json'), JSON.stringify({ name: NAME, at: new Date().toISOString(), screens: results }, null, 1));
  fs.writeFileSync(path.join(OUT, 'index.html'), html(results));
  const withIssues = results.filter((r) => r.bad.length).length;
  const line = `${NAME}: ${results.length} экранов, замечания по правилам design/specs — на ${withIssues}`;
  console.log(`\n== ${line}`);
  if (process.env.GITHUB_ACTIONS) {
    // Одной отметкой — по строке на экран: сводку прогона без входа в GitHub не видно, отметки — видно
    const lines = results.map((r) => {
      const m = r.m || {};
      const ex = [...(m.small || []).slice(0, 2), ...(m.smallTargets || []).slice(0, 2), ...(m.clipped || []).slice(0, 1)];
      return `${r.path} — ${r.bad.join('; ') || 'ок'}${ex.length ? ` (напр.: ${ex.join('; ')})` : ''}`;
    });
    const text = `${line}. Снимки и index.html — артефакт design-…\n${lines.join('\n')}`.slice(0, 6000);
    console.log(`::notice title=Дизайн · ${NAME.replace(/%/g, '%25').replace(/,/g, '%2C').replace(/:/g, '%3A')}::${text.replace(/%/g, '%25').replace(/\n/g, '%0A')}`);
  }
  if (process.env.GITHUB_STEP_SUMMARY) {
    const cell = (x) => String(x).replace(/\|/g, '\\|');
    let md = `### Дизайн — ${NAME}\n\n${line}. Снимки и замер — в артефакте задания (папка design, index.html).\n\n| Экран | Ширина | Размеры шрифта | Замечания |\n|---|---|---|---|\n`;
    for (const r of results) md += `| ${cell(r.what)} \`${r.path}\` | ${r.m ? r.m.width : '—'} | ${r.m ? r.m.sizes.join(', ') : '—'} | ${cell(r.bad.join('; ') || 'нет')} |\n`;
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, md + '\n');
  }
}

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

main().catch((e) => {
  console.log(`Дизайн: замер не получился — ${String(e.message || e).split('\n')[0]}`);
  if (process.env.GITHUB_ACTIONS) console.log(`::warning title=Дизайн::замер не получился — ${String(e.message || e).split('\n')[0]}`);
  process.exit(0);
});

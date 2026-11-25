'use strict';
// Автотесты UI из qa/ сайта — внутри приложения Android на эмуляторе (T7, ГД 25.11: «все кейсы P0/P1 QA — на эмуляторе»).
// Файлы QA не меняем: этот файл грузится раньше тестов (NODE_OPTIONS=--require=…) и подменяет в qa/helpers двух
// помощников, остальное — как в Chromium:
//   browser.js → launch()   — не Chromium, а эмулятор через Playwright для Android (_android, нужен adb);
//                openPage() — страница сайта внутри WebView приложения: приложение с нуля (pm clear), состояние
//                             сайта (state) — в localStorage до его скриптов, ошибки JS — в page.qaErrors, как у QA;
//   server.js  → start()    — общий демо-сервер CI (QA_BASE) вместо своего на случайном порту: приложение (debug)
//                             видит только свой сервер. Тест со своим режимом (snapshot) или env — ошибка с причиной.
// QA_BASE — http://10.0.2.2:3000: так сервер видят и приложение на эмуляторе, и сами тесты (на машине CI адрес 10.0.2.2
// добавлен к lo — tools/ci/qa/in-app.sh). Экран — телефона эмулятора: device из теста (MOBILE, DESKTOP) не меняет ширину.
const path = require('node:path');
const os = require('node:os');

const WEB = process.env.CC_WEB;
const PKG = process.env.QA_ANDROID_PKG || 'ar.canastacerca';
const BASE = (process.env.QA_BASE || 'http://10.0.2.2:3000').replace(/\/$/, '');
const PERMS = ['android.permission.CAMERA', 'android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION'];

if (WEB && process.env.QA_ANDROID === '1') {
  const browser = require(path.join(WEB, 'qa', 'helpers', 'browser.js'));
  const server = require(path.join(WEB, 'qa', 'helpers', 'server.js'));

  server.start = async ({ mode = 'demo', env = {} } = {}) => {
    const own = Object.keys(env || {});
    if (mode !== 'demo' || own.length) {
      throw new Error(`QA_ANDROID: тесту нужен свой сервер (${mode}${own.length ? `, env ${own.join(', ')}` : ''}) — в приложении только общий демо-сервер ${BASE}`);
    }
    return { base: BASE, port: Number(new URL(BASE).port) || 80, log: () => '', dataDir: os.tmpdir(), stop: async () => {} };
  };

  browser.launch = async () => {
    const pw = browser.playwright;
    if (!pw || !pw._android) throw new Error('QA_ANDROID: нет Playwright для Android (npm i --no-save playwright)');
    const [device] = await pw._android.devices();
    if (!device) throw new Error('QA_ANDROID: adb не видит эмулятор');
    return { qaAndroid: device, close: async () => device.close(), newContext: () => { throw new Error('QA_ANDROID: в приложении один WebView — newContext нельзя'); } };
  };

  const original = browser.openPage;
  browser.openPage = async (b, base, { device = browser.DESKTOP, state = null, onboard = false } = {}) => {
    const d = b && b.qaAndroid;
    if (!d) return original(b, base, { device, state, onboard });
    if (!onboard) state = { onboardPay: 'done', ...(state || {}) };
    // adb изредка отвечает FAIL сразу после pm clear — повторяем
    const sh = async (cmd) => {
      for (let i = 0; ; i++) {
        try {
          return (await d.shell(cmd)).toString().trim();
        } catch (e) {
          if (i >= 3) throw e;
          await new Promise((ok) => setTimeout(ok, 700));
        }
      }
    };
    await sh(`am force-stop ${PKG}`);
    await sh(`pm clear ${PKG}`);
    for (const p of PERMS) await sh(`pm grant ${PKG} ${p}`).catch(() => {});
    await sh(`am start -W -n ${PKG}/.MainActivity`);
    // WebView именно этого запуска (pid): после pm clear старый ещё может числиться у adb
    const until = Date.now() + 30000;
    let wv = null;
    while (!wv && Date.now() < until) {
      const pid = Number(await sh(`pidof ${PKG}`)) || 0;
      wv = d.webViews().find((w) => w.pkg() === PKG && w.pid() === pid) || null;
      if (!wv) await new Promise((ok) => setTimeout(ok, 300));
    }
    if (!wv) wv = await d.webView({ pkg: PKG }, { timeout: 10000 });
    const page = await wv.page();
    await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
    // Как новая вкладка у QA: пустое хранилище; state кладётся до скриптов сайта при каждой загрузке, если там пусто
    if (state) {
      await page.addInitScript(
        ([k, v]) => {
          try {
            if (!localStorage.getItem(k)) localStorage.setItem(k, v);
          } catch {}
        },
        [browser.STORAGE_KEY, JSON.stringify(state)],
      );
    }
    await page.evaluate(() => {
      try {
        localStorage.clear();
        sessionStorage.clear();
      } catch {}
    });
    if (process.env.QA_NET !== '1') await page.route((url) => !url.href.startsWith(base), (route) => route.abort()).catch(() => {});
    const errors = [];
    page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
    page.on('console', (m) => {
      if (m.type() !== 'error') return;
      const text = m.text();
      if (/Failed to load resource|net::ERR_|ERR_FAILED/.test(text)) return;
      errors.push(`console.error: ${text}`);
    });
    page.qaErrors = errors;
    page.qaDevice = device;
    page.qaClose = async () => {};
    return page;
  };
}

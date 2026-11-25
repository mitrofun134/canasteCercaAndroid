"""Проверка оболочки Android против настоящего сайта (демо-режим): тот же JS, что внедряет MainActivity.kt.

Берёт строки прямо из MainActivity.kt (bridgeJs, CLOSE_DIALOG, OPEN_IN_PLACE, PULL_OK), подставляет их в Chrome
с телефонным экраном и без navigator.share (как в WebView), а объект CanastaNative изображает WebMessageListener.

Запуск (Python 3 и Playwright: pip install playwright && playwright install chromium):
  cd ~/Downloads/canasta-cerca && PORT=3077 node server.js --demo &
  cd ~/StudioProjects/canasta-cerca-android && python3 tools/bridge_test.py app/src/main/kotlin/ar/canastacerca/MainActivity.kt
Свой Chrome вместо Chromium из Playwright: CHROME=/путь/к/chrome. Снимки страниц — в папку из SHOTS (если задана).
Версию сборки (versionName, versionCode) берёт из app/build.gradle.kts рядом с MainActivity.kt.
"""
import json
import os
import re
import sys
import textwrap

from playwright.sync_api import sync_playwright

KT = sys.argv[1]
BASE = sys.argv[2] if len(sys.argv) > 2 else 'http://localhost:3077'
src = open(KT, encoding='utf-8').read()

# --- JS из Kotlin ---
raw = re.search(r'return """\n(.*?)""".trimIndent\(\)', src, re.S).group(1)
raw = textwrap.dedent(raw).strip('\n')
gradle = open(os.path.join(os.path.dirname(os.path.abspath(KT)), *(['..'] * 5), 'build.gradle.kts'), encoding='utf-8').read()
version_name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
version_code = re.search(r'versionCode = (\d+)', gradle).group(1)
info = json.dumps({'platform': 'android', 'version': f'{version_name}+{version_code}', 'push': 'off'}, separators=(',', ':'))
native = re.search(r'const val NATIVE = "(\w+)"', src).group(1)


def bridge(share=True):
    js = raw.replace('${JSONObject.quote(info)}', json.dumps(info)).replace('$NATIVE', native)
    js = js.replace('$shareOn', 'true' if share else 'false')
    assert '$' not in js, js
    return js


bridge_js = bridge()


def kconst(name):
    m = re.search(rf'const val {name} =\s*\n?\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src)
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    return ''.join(json.loads(f'"{p}"') for p in parts)


CLOSE_DIALOG = kconst('CLOSE_DIALOG')
OPEN_IN_PLACE = kconst('OPEN_IN_PLACE')
PULL_OK = kconst('PULL_OK')
PAGE_ASSETS = kconst('PAGE_ASSETS')
CAN_RELOAD = kconst('CAN_RELOAD')
FRESH_RELOAD = kconst('FRESH_RELOAD')
SETTLED = kconst('SETTLED')
# Регулярка SITE_ASSET из Kotlin: в Kotlin-строке \\ → \, \" → "
SITE_ASSET = re.compile(json.loads('"' + re.search(r'val SITE_ASSET = Regex\("((?:[^"\\]|\\.)*)"\)', src).group(1) + '"'))

# WebView: нет navigator.share; объект моста — как у WebMessageListener (postMessage + onmessage)
FAKE_NATIVE = """
delete Navigator.prototype.share; delete Navigator.prototype.canShare;
window.__sent = [];
window.%s = {
  postMessage(s) {
    const m = JSON.parse(s); window.__sent.push(m);
    setTimeout(() => this.onmessage && this.onmessage({ data: JSON.stringify({ call: m.call, ok: true }) }), 20);
  }
};
""" % native

UA = ('Mozilla/5.0 (Linux; Android 14; Pixel 7; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 '
      f'Chrome/153.0.0.0 Mobile Safari/537.36 CanastaCercaAndroid/{version_name}')

results = []


def check(name, ok, detail=''):
    results.append((name, bool(ok), detail))
    print(('PASS ' if ok else 'FAIL ') + name + (f' — {detail}' if detail else ''))


with sync_playwright() as p:
    b = p.chromium.launch(executable_path=os.environ.get('CHROME') or None, args=['--no-sandbox'])
    ctx = b.new_context(viewport={'width': 390, 'height': 844}, device_scale_factor=2, is_mobile=True, has_touch=True,
                        user_agent=UA, locale='es-AR')
    ctx.add_init_script(FAKE_NATIVE)
    ctx.add_init_script(bridge_js)
    page = ctx.new_page()
    errors, api_tags, events, outside = [], [], [], []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.on('console', lambda m: m.type == 'error' and errors.append('console: ' + m.text))

    def on_req(r):
        if '/api/' in r.url and '/api/event' not in r.url and '/api/image' not in r.url and '/api/logo' not in r.url:
            api_tags.append(r.headers.get('x-cc-client'))
        if '/api/event' in r.url and r.method == 'POST':
            try:
                events.append(json.loads(r.post_data or '{}'))
            except Exception:
                pass
    page.on('request', on_req)
    # Чужие сайты не грузим (в сессии нет интернета); WhatsApp — запоминаем
    page.route(re.compile(r'^https?://(?!localhost)'), lambda route: (outside.append(route.request.url), route.abort()))

    page.goto(BASE + '/', wait_until='networkidle')
    page.wait_for_timeout(1500)

    a = page.evaluate('appInfo()')
    check('сайт видит приложение: appInfo()', a and a.get('platform') == 'android' and a.get('version') == f'{version_name}+{version_code}', json.dumps(a))
    check('inApp() === true', page.evaluate('inApp()'))
    tags = [t for t in api_tags if t]
    check(f'X-CC-Client в запросах /api — android/{version_name}+{version_code} web/…', tags and all(t.startswith(f'android/{version_name}+{version_code} web/') for t in tags),
          f'{len(tags)} запросов, пример: {tags[0] if tags else None}')
    check('navigator.share подменён мостом (appShareInstall)', page.evaluate('navigator.__ccAppShare === true'))

    # «Compartir» текстом
    r = page.evaluate("navigator.share({ text: 'Mi canasta sale $12.300 hoy en Coto', url: 'https://canastacerca.com/canasta?c=abc' }).then(() => 'ok', e => 'err:' + e.name)")
    sent = page.evaluate('window.__sent.slice(-1)[0]')
    check('share текстом: сайт ждёт ответ и получает ok', r == 'ok', r)
    check('share текстом: в приложение уходит {call, arg:{text,url}}', sent and sent['call'] == 'share' and sent['arg'].get('url', '').startswith('https://canastacerca.com/canasta'),
          json.dumps(sent, ensure_ascii=False)[:160])

    # «Compartir mi ahorro» картинкой
    r = page.evaluate("""(async () => {
      const c = document.createElement('canvas'); c.width = 40; c.height = 40;
      c.getContext('2d').fillRect(0, 0, 40, 40);
      const blob = await new Promise(ok => c.toBlob(ok, 'image/png'));
      const file = new File([blob], 'mi-ahorro.png', { type: 'image/png' });
      if (!navigator.canShare({ files: [file] })) return 'canShare=false';
      return navigator.share({ files: [file], text: 'Este mes ahorré $48.300' }).then(() => 'ok', e => 'err:' + e.name);
    })()""")
    sent = page.evaluate('window.__sent.slice(-1)[0]')
    img = (sent or {}).get('arg', {}).get('image', '')
    check('share картинкой: canShare и ответ ok', r == 'ok', r)
    check('share картинкой: image — data:image/png;base64', img.startswith('data:image/png;base64,'), img[:40])
    # тот же разбор data: URL, что в Kotlin (DATA_URL)
    m = re.match(r'^data:(image/(?:png|jpeg|webp));base64,(.+)$', img, re.S)
    check('картинка разбирается регуляркой DATA_URL из MainActivity', bool(m))

    # «Назад» закрывает панель сайта: открываем окно адреса так, как его открывает сайт
    opened = page.evaluate("""(() => { const d = document.getElementById('dlg-loc'); if (!d) return 'no dlg-loc';
      d.showModal(); return d.open; })()""")
    first = page.evaluate(CLOSE_DIALOG)
    still = page.evaluate("document.getElementById('dlg-loc').open")
    second = page.evaluate(CLOSE_DIALOG)
    check('«назад» закрывает открытую панель (<dialog>)', opened is True and first is True and still is False, f'opened={opened} first={first} still={still}')
    check('«назад» без панели — дальше по истории (CLOSE_DIALOG = false)', second is False)

    # haptic(): лёгкий отклик при «Agregar» (T3) — сообщение без ответа
    n0 = page.evaluate('window.__sent.length')
    page.evaluate("CanastaApp.haptic()")
    sent = page.evaluate('window.__sent.slice(-1)[0]')
    check('haptic(): в приложение уходит {call: haptic, arg: {kind: tap}}', page.evaluate('window.__sent.length') == n0 + 1 and sent
          and sent['call'] == 'haptic' and sent['arg'].get('kind') == 'tap', json.dumps(sent))

    # «Потянуть — обновить» (PULL_OK): только наверху, без панели, не с карусели
    page.evaluate('window.scrollTo(0, 0)')
    page.wait_for_timeout(300)
    plain = page.evaluate("""(() => {
      for (let y = 8; y < innerHeight; y += 12) for (const x of [20, 195, 370]) {
        const el = document.elementFromPoint(x, y);
        if (!el) continue;
        let ok = true;
        for (let e = el; e && e !== document.body; e = e.parentElement) {
          const s = getComputedStyle(e);
          if (/auto|scroll/.test(s.overflowX + s.overflowY) || ['INPUT', 'CANVAS'].includes(e.tagName)) { ok = false; break; }
        }
        if (ok) return [x, y];
      }
      return null;
    })()""")
    at_plain = page.evaluate(f'{PULL_OK}({plain[0]},{plain[1]})') if plain else None
    check('PULL_OK: наверху страницы, обычное место — можно тянуть', at_plain is True, f'точка {plain}')
    page.evaluate("document.getElementById('dlg-loc').showModal()")
    at_dlg = page.evaluate(f'{PULL_OK}({plain[0]},{plain[1]})') if plain else None
    page.evaluate(CLOSE_DIALOG)
    check('PULL_OK: открыта панель (<dialog>) — нельзя', at_dlg is False)
    page.evaluate('window.scrollTo(0, 400)')
    page.wait_for_timeout(300)
    at_scrolled = page.evaluate(f'{PULL_OK}(195,300)')
    page.evaluate('window.scrollTo(0, 0)')
    check('PULL_OK: страница не в самом верху — нельзя', at_scrolled is False)

    # window.open → WhatsApp открывается снаружи
    page.evaluate(OPEN_IN_PLACE)
    try:
        page.evaluate("window.open('https://wa.me/?text=hola', '_blank', 'noopener')")
        page.wait_for_timeout(800)
    except Exception as e:
        pass
    check('window.open(wa.me) уходит в навигацию (её перехватит приложение)', any('wa.me' in u for u in outside), str([u for u in outside if 'wa.me' in u][:1]))

    # События с pl: android
    page.goto(BASE + '/canasta', wait_until='networkidle')
    page.wait_for_timeout(1000)
    pls = sorted({e.get('pl') for e in events})
    check('события /api/event идут с pl: android', events and pls == ['android'], f'{len(events)} событий, pl={pls}')

    # Ширина страниц на телефоне 390 px и ошибки JS
    widths = {}
    for path in ['/', '/canasta', '/catalogo', '/recetas', '/descuentos', '/ahorro']:
        page.goto(BASE + path, wait_until='networkidle')
        page.wait_for_timeout(700)
        widths[path] = page.evaluate('document.documentElement.scrollWidth')
        if os.environ.get('SHOTS'):
            page.screenshot(path=os.path.join(os.environ['SHOTS'], f'shot{path.replace("/", "_")}.png'))
    wide = {k: v for k, v in widths.items() if v > 390}
    check('страницы не шире экрана 390 px', not wide, json.dumps(widths))
    # PULL_OK на карусели, которую листают пальцем (в демо на главной их нет — «Recetas» и «Descuentos»)
    for path in ['/recetas', '/descuentos']:
        page.goto(BASE + path, wait_until='networkidle')
        page.wait_for_timeout(700)
        page.evaluate('window.scrollTo(0, 0)')
        car = page.evaluate("""(() => {
          const sc = [...document.querySelectorAll('body *')].find((el) => {
            const s = getComputedStyle(el), r = el.getBoundingClientRect();
            return (s.overflowX === 'auto' || s.overflowX === 'scroll') && el.scrollWidth > el.clientWidth + 1
              && r.top >= 0 && r.bottom <= innerHeight && r.width > 100 && r.height > 30;
          });
          if (!sc) return null;
          const r = sc.getBoundingClientRect();
          return { x: r.left + r.width / 2, y: r.top + r.height / 2, cls: String(sc.className).slice(0, 30) };
        })()""")
        at_car = page.evaluate(f"{PULL_OK}({car['x']},{car['y']})") if car else None
        check(f'PULL_OK: палец на карусели ({path}) — нельзя', at_car is False, json.dumps(car))

    real_errors = [e for e in errors if 'net::ERR' not in e and 'Failed to load resource' not in e]
    check('нет ошибок JS', not real_errors, '; '.join(real_errors[:5]))

    # Обновление сайта (§3.1): версия страницы против сервера — на неизменённом сайте разницы нет, на новом — есть
    import urllib.request
    same = {}
    for path in ['/', '/catalogo', '/recetas', '/descuentos', '/canasta', '/ahorro']:
        page.goto(BASE + path, wait_until='networkidle')
        page.wait_for_timeout(500)
        server = set(SITE_ASSET.findall(urllib.request.urlopen(BASE + path).read().decode('utf-8')))
        have = set(page.evaluate(PAGE_ASSETS))
        same[path] = bool(server) and server <= have
    check('версия сайта: страница = сервер на 6 страницах (лишней перезагрузки нет)', all(same.values()), json.dumps(same))
    server = sorted(SITE_ASSET.findall(urllib.request.urlopen(BASE + '/').read().decode('utf-8')))
    newer = set(server[1:]) | {server[0].split('?v=')[0] + '?v=NEW'}
    page.goto(BASE + '/', wait_until='networkidle')
    have = set(page.evaluate(PAGE_ASSETS))
    check('версия сайта: новый ?v= на сервере — заметно', not newer <= have, f'{len(server)} файлов с версией')
    page.evaluate("document.getElementById('dlg-loc').showModal()")
    busy = page.evaluate(CAN_RELOAD)
    page.evaluate(CLOSE_DIALOG)
    free = page.evaluate(CAN_RELOAD)
    check('перезагрузка ждёт: с открытой панелью — нет, без неё — да', busy is False and free is True, f'{busy} {free}')
    page.evaluate("""(async () => {
      const c = await caches.open('cc-test');
      for (const u of ['/', '/recetas', '/style.css?v=1', '/api/config', '/img/x.png']) await c.put(u, new Response('x'));
    })()""")
    page.evaluate(FRESH_RELOAD)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(500)
    left = page.evaluate("caches.open('cc-test').then(c => c.keys()).then(rs => rs.map(r => new URL(r.url).pathname + new URL(r.url).search).sort())")
    check('свежая перезагрузка: из кэша сайта убраны страницы, файлы и данные остались', left == ['/api/config', '/img/x.png', '/style.css?v=1'], json.dumps(left))

    # Перезагрузка под снимком (freshReload): SETTLED — «сайт дорисовался». Во время заглушек — нет, потом — да
    settled = {}
    # /recetas нет: в демо цены блюд не считаются (server.js: «no están en modo demo») и заглушки цены остаются навсегда —
    # снимок тогда уходит по таймауту 3 с; на проде цены приходят
    for path in ['/', '/catalogo', '/descuentos', '/canasta', '/ahorro']:
        page.goto(BASE + path, wait_until='networkidle')
        page.wait_for_timeout(1500)
        settled[path] = page.evaluate(SETTLED)
    check('SETTLED: после загрузки на 5 страницах — да (снимок не висит до таймаута)', all(v is True for v in settled.values()), json.dumps(settled))
    page.goto(BASE + '/', wait_until='networkidle')
    page.wait_for_timeout(1000)
    page.evaluate(FRESH_RELOAD)
    seen = []
    for _ in range(60):
        try:
            seen.append(page.evaluate(SETTLED))
        except Exception:
            seen.append(None)
        if seen[-1] is True and False in seen:
            break
        page.wait_for_timeout(50)
    check('SETTLED: сразу после перезагрузки — нет (заглушки), потом — да', False in seen and seen[-1] is True,
          f'{len(seen)} опросов: ' + ''.join('·' if v is None else ('+' if v else '-') for v in seen))

    # Флаг app.flags.share = false (§3.6): в мосте нет share — сайт делает «Compartir» как в браузере
    ctx2 = b.new_context(viewport={'width': 390, 'height': 844}, device_scale_factor=2, is_mobile=True, has_touch=True,
                         user_agent=UA, locale='es-AR')
    ctx2.add_init_script(FAKE_NATIVE)
    ctx2.add_init_script(bridge(share=False))
    ctx2.route(re.compile(r'^https?://(?!localhost)'), lambda route: route.abort())
    p2 = ctx2.new_page()
    p2.goto(BASE + '/', wait_until='networkidle')
    p2.wait_for_timeout(800)
    no_share = p2.evaluate("({ app: typeof CanastaApp, share: typeof CanastaApp.share, inApp: inApp(), patched: navigator.__ccAppShare === true })")
    check('флаг share выключен: моста share нет, сайт не подменяет navigator.share', no_share == {'app': 'object', 'share': 'undefined', 'inApp': True, 'patched': False},
          json.dumps(no_share))
    b.close()

failed = [r for r in results if not r[1]]
print(f'\n{len(results) - len(failed)}/{len(results)} проверок прошли')
sys.exit(1 if failed else 0)

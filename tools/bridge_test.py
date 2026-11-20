"""Проверка оболочки Android против настоящего сайта (демо-режим): тот же JS, что внедряет MainActivity.kt.

Берёт строки прямо из MainActivity.kt (bridgeJs, CLOSE_DIALOG, OPEN_IN_PLACE), подставляет их в Chrome
с телефонным экраном и без navigator.share (как в WebView), а объект CanastaNative изображает WebMessageListener.

Запуск (Python 3 и Playwright: pip install playwright && playwright install chromium):
  cd ~/Downloads/canasta-cerca && PORT=3077 node server.js --demo &
  cd ~/StudioProjects/canasta-cerca-android && python3 tools/bridge_test.py app/src/main/kotlin/ar/canastacerca/MainActivity.kt
Свой Chrome вместо Chromium из Playwright: CHROME=/путь/к/chrome. Снимки страниц — в папку из SHOTS (если задана).
Версию сборки сверяет с app/build.gradle.kts: после смены versionName/versionCode поправить version_name, version_code.
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
version_name, version_code = '0.3.1', '4'
info = json.dumps({'platform': 'android', 'version': f'{version_name}+{version_code}', 'push': 'off'}, separators=(',', ':'))
native = re.search(r'const val NATIVE = "(\w+)"', src).group(1)
bridge_js = raw.replace('${JSONObject.quote(info)}', json.dumps(info)).replace('$NATIVE', native)
assert '$' not in bridge_js, bridge_js


def kconst(name):
    m = re.search(rf'const val {name} =\s*\n?\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src)
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    return ''.join(json.loads(f'"{p}"') for p in parts)


CLOSE_DIALOG = kconst('CLOSE_DIALOG')
OPEN_IN_PLACE = kconst('OPEN_IN_PLACE')

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
    real_errors = [e for e in errors if 'net::ERR' not in e and 'Failed to load resource' not in e]
    check('нет ошибок JS', not real_errors, '; '.join(real_errors[:5]))
    b.close()

failed = [r for r in results if not r[1]]
print(f'\n{len(results) - len(failed)}/{len(results)} проверок прошли')
sys.exit(1 if failed else 0)

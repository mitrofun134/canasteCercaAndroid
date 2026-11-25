'use strict';
// Замер экрана для дизайнера (T7): выполняется на странице (page.evaluate), страница — сайт в WebView приложения.
// Правила — из design/specs сайта:
//   MOVIL-REPASO.md — текста меньше 12 px нет (кроме логотипов), размеров шрифта на странице не больше 7, начертаний
//                     500 и 900 нет, обрезанного текста нет, боковой отступ 16 px;
//   ESPACIO.md §7, MOVIL-COMPACTO.md §12 (D60) — нажимаемое от 44 px;
//   ESPACIO.md — закреплённое (шапка, полосы, меню) на телефоне не больше 40 % первого экрана и 18 % после прокрутки.
// Считается первые три экрана страницы. Возвращает числа и примеры — без оценок «хорошо/плохо» (их ставит report).
module.exports = function measure() {
  const W = innerWidth;
  const vh = innerHeight;
  const LIM = 3 * vh;
  const sy = scrollY;
  const fixedOf = (el) => {
    for (let e = el; e && e !== document.body && e !== document.documentElement; e = e.parentElement) {
      const p = getComputedStyle(e).position;
      if (p === 'fixed' || p === 'sticky') return e;
    }
    return null;
  };
  const visible = (el) => {
    const cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || Number(cs.opacity) === 0) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const label = (el) => {
    const cls = typeof el.className === 'string' && el.className.trim() ? '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.') : '';
    const txt = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('alt') || '').replace(/\s+/g, ' ').trim().slice(0, 24);
    return `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}${cls}${txt ? ` «${txt}»` : ''}`;
  };
  const isLogo = (el) => !!el.closest('[class*="logo"], [class*="Logo"]');

  // Текст: размеры и начертания, мелкий текст, боковые отступы
  const sizes = {};
  const weights = {};
  const small = [];
  const heavy = [];
  let left = Infinity;
  let right = Infinity;
  const tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const rng = document.createRange();
  while (tw.nextNode()) {
    const t = tw.currentNode;
    const s = t.textContent.replace(/\s+/g, ' ').trim();
    if (!s || !/[\p{L}\p{N}]/u.test(s)) continue;
    const el = t.parentElement;
    if (!el || ['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'OPTION'].includes(el.tagName) || !visible(el)) continue;
    rng.selectNodeContents(t);
    const rs = [...rng.getClientRects()].filter((r) => r.width > 0 && r.height > 0);
    if (!rs.length) continue;
    const top = rs[0].top + (fixedOf(el) ? 0 : sy);
    if (top > LIM) continue;
    const cs = getComputedStyle(el);
    const px = Math.round(parseFloat(cs.fontSize) * 10) / 10;
    sizes[px] = (sizes[px] || 0) + 1;
    weights[cs.fontWeight] = (weights[cs.fontWeight] || 0) + 1;
    if (px < 12 && !isLogo(el) && small.length < 10) small.push(`${s.slice(0, 26)} — ${px} px`);
    if ((cs.fontWeight === '500' || cs.fontWeight === '900') && heavy.length < 6) heavy.push(`${s.slice(0, 26)} — ${cs.fontWeight}`);
    // Отступ от края — только у текста в пределах экрана по ширине и не в карусели (её ряд уходит за край нарочно)
    const inRow = el.closest('.hscroll, [class*="scroll"], [class*="carousel"], [class*="row"]');
    for (const r of rs) {
      if (r.right <= 0 || r.left >= W || inRow) continue;
      left = Math.min(left, r.left);
      right = Math.min(right, W - r.right);
    }
  }

  // Нажимаемое меньше 44 px (ссылка внутри строки текста — не в счёт)
  const smallTargets = [];
  let targets = 0;
  for (const el of document.querySelectorAll('a[href], button, [role="button"], input:not([type="hidden"]), select, textarea, summary, label[for]')) {
    if (!visible(el) || el.closest('[aria-hidden="true"], [inert]')) continue;
    const r = el.getBoundingClientRect();
    const top = r.top + (fixedOf(el) ? 0 : sy);
    if (top > LIM || r.right <= 0 || r.left >= W) continue;
    const cs = getComputedStyle(el);
    if (el.tagName === 'A' && cs.display === 'inline' && el.parentElement && el.parentElement.innerText.trim().length > el.innerText.trim().length + 3) continue;
    targets++;
    if ((r.height < 44 || r.width < 44) && smallTargets.length < 12) smallTargets.push(`${label(el)} ${Math.round(r.width)}×${Math.round(r.height)}`);
  }

  // Обрезанный текст: многоточие или line-clamp, а текст не влез
  const clipped = [];
  for (const el of document.querySelectorAll('body *')) {
    const cs = getComputedStyle(el);
    const clamp = cs.webkitLineClamp && cs.webkitLineClamp !== 'none';
    if (cs.textOverflow !== 'ellipsis' && !clamp) continue;
    if (!visible(el)) continue;
    const r = el.getBoundingClientRect();
    if (r.top + sy > LIM) continue;
    if (el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 1) {
      if (clipped.length < 10) clipped.push(label(el));
    }
  }

  // Закреплённое: сколько первого экрана занимают fixed и sticky (верхние, без вложенных)
  const fixedShare = () => {
    let h = 0;
    for (const el of document.querySelectorAll('body *')) {
      const p = getComputedStyle(el).position;
      if ((p !== 'fixed' && p !== 'sticky') || !visible(el) || fixedOf(el.parentElement)) continue;
      const r = el.getBoundingClientRect();
      const a = Math.max(0, r.top);
      const b = Math.min(vh, r.bottom);
      if (b > a && r.width > W / 2) h += b - a;
    }
    return Math.round((100 * h) / vh);
  };

  return {
    width: W,
    height: vh,
    sideScroll: Math.max(0, document.documentElement.scrollWidth - W),
    screens: Math.round((document.documentElement.scrollHeight / vh) * 10) / 10,
    sizes: Object.keys(sizes).map(Number).sort((a, b) => a - b),
    weights: Object.keys(weights).sort(),
    small,
    heavy,
    targets,
    smallTargets,
    clipped,
    left: left === Infinity ? null : Math.round(left),
    right: right === Infinity ? null : Math.round(right),
    fixedTop: fixedShare(),
  };
};

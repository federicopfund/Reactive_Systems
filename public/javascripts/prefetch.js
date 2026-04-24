/* ================================================================
 * prefetch.js — Prefetch on hover (Issue #18-H)
 *
 * Hace prefetch de la URL apuntada por una <a class="ed-article-card">
 * o cualquier <a data-prefetch> tras 200 ms de hover, una sola vez por URL.
 * Respeta Save-Data y reduces conexión 2g/slow-2g.
 * ================================================================ */
(function () {
  'use strict';

  // Bail out en conexiones lentas / Data Saver activo.
  var c = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  if (c && (c.saveData || /2g/i.test(c.effectiveType || ''))) return;

  var INTENT_DELAY = 200;
  var prefetched = new Set();

  function prefetch(url) {
    if (prefetched.has(url)) return;
    prefetched.add(url);
    var link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = url;
    link.as = 'document';
    document.head.appendChild(link);
  }

  function bind(a) {
    if (a.dataset.prefetchBound === '1') return;
    a.dataset.prefetchBound = '1';
    var timer = null;
    a.addEventListener('mouseenter', function () {
      timer = setTimeout(function () {
        var u = a.getAttribute('href');
        if (u && /^\/[^\/]/.test(u)) prefetch(u);
      }, INTENT_DELAY);
    });
    a.addEventListener('mouseleave', function () {
      clearTimeout(timer);
    });
  }

  function scan() {
    document.querySelectorAll('a.ed-article-card, a[data-prefetch="true"]').forEach(bind);
  }

  if (document.readyState !== 'loading') scan();
  else document.addEventListener('DOMContentLoaded', scan);

  // Re-escanear si el DOM cambia (filtros AJAX futuros).
  var mo = new MutationObserver(function () { scan(); });
  mo.observe(document.documentElement, { childList: true, subtree: true });
})();

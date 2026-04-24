/* ================================================================
 * animations.js — Stagger de cards + theme transition (Issue #18-C)
 *
 * - Asigna --i incremental a .ed-article-card (animation-delay).
 * - Al cambiar data-theme, aplica clase .ed-theming durante 400 ms
 *   para crossfade suave de background-color/color/border.
 * - Sin nada si prefers-reduced-motion: reduce.
 * ================================================================ */
(function () {
  'use strict';

  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) return;

  // 1. Stagger de cards al primer paint.
  function staggerCards() {
    var cards = document.querySelectorAll('.ed-articles-grid > .ed-article-card');
    cards.forEach(function (c, i) {
      if (i < 12) c.style.setProperty('--i', i); // limita el delay max
    });
  }

  // 2. Theme crossfade.
  function watchTheme() {
    var html = document.documentElement;
    var lastTheme = html.getAttribute('data-theme');
    var observer = new MutationObserver(function (records) {
      records.forEach(function (r) {
        if (r.attributeName !== 'data-theme') return;
        var t = html.getAttribute('data-theme');
        if (t === lastTheme) return;
        lastTheme = t;
        html.classList.add('ed-theming');
        setTimeout(function () { html.classList.remove('ed-theming'); }, 400);
      });
    });
    observer.observe(html, { attributes: true, attributeFilter: ['data-theme'] });
  }

  if (document.readyState !== 'loading') { staggerCards(); watchTheme(); }
  else document.addEventListener('DOMContentLoaded', function () { staggerCards(); watchTheme(); });
})();

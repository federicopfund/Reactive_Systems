/* ================================================================
 * animations.js — Stagger de cards + theme transition (Issue #18-C)
 *                 + Adaptive scroll UX para /publicaciones
 *
 * - Asigna --i incremental a .ed-article-card (animation-delay).
 * - Al cambiar data-theme, aplica clase .ed-theming durante 400 ms
 *   para crossfade suave de background-color/color/border.
 * - Toolbar colapsa al hacer scroll (is-shrunk).
 * - Cards bajo el fold se revelan con IntersectionObserver.
 * - Botón flotante "volver arriba" inyectado dinámicamente.
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

  // 3. Adaptive scroll: toolbar collapse + card scroll-reveal + back-to-top.
  function initPublicationsScroll() {
    var toolbar = document.querySelector('.ed-pub-toolbar');
    var grid    = document.querySelector('.ed-articles-grid');

    if (!toolbar && !grid) return;

    // ── 3a. Back-to-top button ──────────────────────────────────
    var btn = document.createElement('button');
    btn.className = 'ed-backtop';
    btn.setAttribute('aria-label', 'Volver arriba');
    btn.setAttribute('type', 'button');
    btn.innerHTML = '&#8593;'; // ↑
    document.body.appendChild(btn);

    btn.addEventListener('click', function () {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    // ── 3b. Card scroll-reveal via IntersectionObserver ─────────
    if (grid) {
      var vh    = window.innerHeight;
      var cards = Array.from(grid.querySelectorAll('.ed-article-card'));

      // Mark below-fold cards: cancel CSS stagger animation and hide them.
      var belowFold = cards.filter(function (c) {
        return c.getBoundingClientRect().top > vh + 40;
      });

      belowFold.forEach(function (c) { c.classList.add('below-fold'); });

      if (belowFold.length > 0 && 'IntersectionObserver' in window) {
        var batchCounter = 0;

        var revealObserver = new IntersectionObserver(function (entries) {
          entries.forEach(function (entry) {
            if (!entry.isIntersecting) return;
            var card  = entry.target;
            var delay = batchCounter % 4; // stagger up to 4 per row
            batchCounter++;
            card.style.setProperty('--reveal-delay', delay);
            card.classList.remove('below-fold');
            card.classList.add('is-scroll-revealed');
            revealObserver.unobserve(card);
          });
        }, { threshold: 0.06, rootMargin: '0px 0px -24px 0px' });

        belowFold.forEach(function (c) { revealObserver.observe(c); });
      } else {
        // Fallback: show all immediately.
        belowFold.forEach(function (c) { c.classList.remove('below-fold'); });
      }
    }

    // ── 3c. Scroll handler: toolbar + back-to-top ───────────────
    var SHRINK_AT  = 30;   // px — toolbar colapsa
    var BACKTOP_AT = 400;  // px — botón aparece
    var ticking    = false;

    function onScroll() {
      var y = window.scrollY;

      if (toolbar) {
        toolbar.classList.toggle('is-shrunk', y > SHRINK_AT);
      }

      btn.classList.toggle('is-visible', y > BACKTOP_AT);

      ticking = false;
    }

    window.addEventListener('scroll', function () {
      if (!ticking) {
        requestAnimationFrame(onScroll);
        ticking = true;
      }
    }, { passive: true });

    // Trigger once on load in case page is restored mid-scroll.
    onScroll();
  }

  if (document.readyState !== 'loading') {
    staggerCards();
    watchTheme();
    initPublicationsScroll();
  } else {
    document.addEventListener('DOMContentLoaded', function () {
      staggerCards();
      watchTheme();
      initPublicationsScroll();
    });
  }
})();

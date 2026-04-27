/**
 * Sidebar controller — Editorial Reactiva
 * Issue #17-A · rail mode (desktop) + drawer mode (mobile).
 *
 * En desktop ≥ 900px: el sidebar puede estar `is-rail` (colapsado a 64px).
 *   Persistencia: localStorage.ed-sidebar-collapsed ('1' | '0').
 *
 * En mobile < 900px: el sidebar funciona como drawer off-canvas.
 *   Estados: `is-open` cuando se abre, overlay con backdrop.
 *
 * Triggers:
 *   - [data-action="toggle-rail"]   → alterna rail en desktop.
 *   - [data-action="open-drawer"]   → abre drawer en mobile.
 *   - [data-action="close-drawer"]  → cierra drawer.
 */
(function () {
  'use strict';

  var KEY_COLLAPSED = 'ed-sidebar-collapsed';
  var BP_MD = 900;

  var sidebar = null;
  var overlay = null;
  var lastFocused = null;

  function isMobile() { return window.innerWidth < BP_MD; }

  function readCollapsed() {
    try { return localStorage.getItem(KEY_COLLAPSED) === '1'; }
    catch (e) { return false; }
  }
  function writeCollapsed(v) {
    try { localStorage.setItem(KEY_COLLAPSED, v ? '1' : '0'); }
    catch (e) { /* ignore */ }
    // Cookie espejo: la lee el partial del sidebar para SSR-rail (anti-FOUC).
    try {
      var maxAge = 60 * 60 * 24 * 365; // 1 año
      document.cookie = KEY_COLLAPSED + '=' + (v ? '1' : '0') +
        '; path=/; max-age=' + maxAge + '; SameSite=Lax';
    } catch (e) { /* ignore */ }
  }

  function applyRail(collapsed) {
    if (!sidebar) return;
    sidebar.classList.toggle('ed-sidebar--rail', !!collapsed);
  }

  function ensureOverlay() {
    if (overlay) return overlay;
    overlay = document.createElement('div');
    overlay.className = 'ed-drawer-overlay';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.addEventListener('click', closeDrawer);
    document.body.appendChild(overlay);
    return overlay;
  }

  function openDrawer() {
    if (!sidebar) return;
    lastFocused = document.activeElement;
    sidebar.classList.add('is-open');
    ensureOverlay().classList.add('is-visible');
    document.body.classList.add('ed-no-scroll');
    var trigger = document.querySelector('[data-action="open-drawer"]');
    if (trigger) trigger.setAttribute('aria-expanded', 'true');
    // Focus al primer link navegable.
    var firstLink = sidebar.querySelector('a, button');
    if (firstLink) firstLink.focus();
  }

  function closeDrawer() {
    if (!sidebar) return;
    sidebar.classList.remove('is-open');
    if (overlay) overlay.classList.remove('is-visible');
    document.body.classList.remove('ed-no-scroll');
    var trigger = document.querySelector('[data-action="open-drawer"]');
    if (trigger) trigger.setAttribute('aria-expanded', 'false');
    if (lastFocused && lastFocused.focus) lastFocused.focus();
  }

  function toggleRail() {
    var next = !readCollapsed();
    writeCollapsed(next);
    applyRail(next);
  }

  function init() {
    sidebar = document.getElementById('ed-sidebar') || document.querySelector('.ed-sidebar');
    if (!sidebar) return;

    // Persistencia del estado de las secciones colapsables (<details data-section>).
    var sections = sidebar.querySelectorAll('details[data-section]');
    sections.forEach(function (d) {
      var key = 'edNavSection:' + d.getAttribute('data-section');
      try {
        var saved = localStorage.getItem(key);
        if (saved === 'open')   d.setAttribute('open', '');
        if (saved === 'closed') d.removeAttribute('open');
      } catch (_) {}
      d.addEventListener('toggle', function () {
        try { localStorage.setItem(key, d.open ? 'open' : 'closed'); } catch (_) {}
      });
    });

    // Aplica preferencia en desktop. En mobile, garantiza que el rail
    // (que pudo venir aplicado por SSR via cookie) quede limpio.
    if (isMobile()) {
      sidebar.classList.remove('ed-sidebar--rail');
    } else {
      // Sincroniza cookie con localStorage por si difieren.
      var collapsed = readCollapsed();
      writeCollapsed(collapsed);
      applyRail(collapsed);
    }

    // Click delegation.
    document.addEventListener('click', function (ev) {
      var t = ev.target.closest && ev.target.closest('[data-action]');
      if (!t) return;
      var action = t.getAttribute('data-action');
      if (action === 'toggle-rail') { ev.preventDefault(); toggleRail(); }
      else if (action === 'open-drawer')  { ev.preventDefault(); openDrawer(); }
      else if (action === 'close-drawer') { ev.preventDefault(); closeDrawer(); }
    });

    // Cierra el drawer al navegar dentro de él.
    sidebar.addEventListener('click', function (ev) {
      if (!isMobile()) return;
      var a = ev.target.closest && ev.target.closest('a[href]');
      if (a) closeDrawer();
    });

    // ESC cierra el drawer.
    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && sidebar.classList.contains('is-open')) closeDrawer();
    });

    // Resize: si pasamos a desktop, cerrar drawer y reaplicar rail.
    var lastMobile = isMobile();
    window.addEventListener('resize', function () {
      var nowMobile = isMobile();
      if (nowMobile !== lastMobile) {
        if (!nowMobile) {
          closeDrawer();
          applyRail(readCollapsed());
        } else {
          sidebar.classList.remove('ed-sidebar--rail');
        }
        lastMobile = nowMobile;
      }
    }, { passive: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

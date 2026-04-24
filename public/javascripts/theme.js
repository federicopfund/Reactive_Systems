/**
 * Theme controller — Editorial Reactiva
 * Issue #16: tres modos (light/dark/auto) con persistencia en localStorage.
 *
 * Modos:
 *   - "crema": tema claro fijo (data-theme="crema")
 *   - "noche": tema oscuro fijo (data-theme="noche")
 *   - "auto":  sigue prefers-color-scheme del SO (data-theme="auto")
 *
 * El bootstrap inline en <head> ya aplica el tema antes del render para
 * evitar FOUC. Este script gestiona el switcher post-load.
 */
(function () {
  'use strict';

  var KEY     = 'ed-theme';
  var VALID   = ['crema', 'noche', 'auto'];
  var DEFAULT = 'auto';
  var root    = document.documentElement;

  function read() {
    var v = null;
    try { v = localStorage.getItem(KEY); } catch (e) { /* private mode */ }
    return VALID.indexOf(v) !== -1 ? v : DEFAULT;
  }

  function write(theme) {
    try { localStorage.setItem(KEY, theme); } catch (e) { /* ignore */ }
  }

  function apply(theme) {
    if (VALID.indexOf(theme) === -1) theme = DEFAULT;
    root.setAttribute('data-theme', theme);
    syncToggles(theme);
    // Disparar evento custom para que componentes reactivos puedan reaccionar.
    document.dispatchEvent(new CustomEvent('ed:themechange', { detail: { theme: theme } }));
  }

  function syncToggles(theme) {
    var btns = document.querySelectorAll('.ed-theme-toggle__btn[data-theme-value]');
    for (var i = 0; i < btns.length; i++) {
      var v = btns[i].getAttribute('data-theme-value');
      var active = (v === theme);
      btns[i].setAttribute('aria-checked', active ? 'true' : 'false');
      btns[i].classList.toggle('is-active', active);
      btns[i].tabIndex = active ? 0 : -1;
    }
  }

  function bind() {
    document.addEventListener('click', function (ev) {
      var btn = ev.target.closest && ev.target.closest('.ed-theme-toggle__btn[data-theme-value]');
      if (!btn) return;
      ev.preventDefault();
      var theme = btn.getAttribute('data-theme-value');
      write(theme);
      apply(theme);
    });

    // Navegación por flechas dentro del switcher (radiogroup pattern).
    document.addEventListener('keydown', function (ev) {
      var btn = ev.target.closest && ev.target.closest('.ed-theme-toggle__btn');
      if (!btn) return;
      var group = btn.closest('.ed-theme-toggle');
      if (!group) return;
      var btns = Array.prototype.slice.call(group.querySelectorAll('.ed-theme-toggle__btn[data-theme-value]'));
      var idx = btns.indexOf(btn);
      var next = null;
      if (ev.key === 'ArrowRight' || ev.key === 'ArrowDown') next = btns[(idx + 1) % btns.length];
      else if (ev.key === 'ArrowLeft' || ev.key === 'ArrowUp') next = btns[(idx - 1 + btns.length) % btns.length];
      else if (ev.key === 'Home') next = btns[0];
      else if (ev.key === 'End') next = btns[btns.length - 1];
      if (next) {
        ev.preventDefault();
        next.focus();
        next.click();
      }
    });
  }

  // API global — útil para tests y para scripts inline.
  window.setEdTheme = function (theme) {
    write(theme);
    apply(theme);
  };
  window.getEdTheme = read;

  // Inicial: aplicar y sincronizar UI cuando el DOM esté listo.
  apply(read());
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind);
  } else {
    bind();
  }
})();

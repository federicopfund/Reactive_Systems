/**
 * events-countdown.js — cuenta regresiva accesible para detalle de evento.
 * Lee `data-countdown-target` (ISO instant) y actualiza
 * `[data-countdown-display]` cada segundo. aria-live=polite en el contenedor.
 */
(function () {
  'use strict';

  function fmt(ms) {
    if (ms <= 0) return 'Ya empezo';
    const sec = Math.floor(ms / 1000);
    const days = Math.floor(sec / 86400);
    const hours = Math.floor((sec % 86400) / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const secs = sec % 60;
    if (days > 0) return `${days}d ${hours}h ${mins}m`;
    if (hours > 0) return `${hours}h ${mins}m ${secs}s`;
    return `${mins}m ${secs}s`;
  }

  function tick(el, target) {
    const display = el.querySelector('[data-countdown-display]');
    if (!display) return;
    const diff = target - Date.now();
    display.textContent = fmt(diff);
    if (diff <= 0) {
      el.classList.add('is-live');
    }
  }

  function init() {
    document.querySelectorAll('[data-countdown-target]').forEach(function (el) {
      const targetIso = el.getAttribute('data-countdown-target');
      const target = Date.parse(targetIso);
      if (isNaN(target)) return;
      tick(el, target);
      setInterval(function () { tick(el, target); }, 1000);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else { init(); }
})();

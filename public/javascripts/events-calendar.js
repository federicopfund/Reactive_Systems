/**
 * events-calendar.js — placeholder para futuras integraciones de calendario
 * (mini-month grid client-side, FullCalendar, etc.). Por ahora deja el hook
 * `[data-events-calendar]` sin operacion para mantener compatibilidad con
 * upgrades posteriores.
 */
(function () {
  'use strict';
  function init() {
    const root = document.querySelector('[data-events-calendar]');
    if (!root) return;
    root.dataset.calendarReady = 'true';
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();

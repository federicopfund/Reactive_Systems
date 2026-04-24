/* ================================================================
 * toast.js — Sistema global de notificaciones (Issue #18-F)
 * API: window.edToast(type, message, options)
 *   type: 'success' | 'error' | 'info' | 'warning'
 *   options: { duration: ms (default 4000), dismissible: bool (default true) }
 * Stack máx 3, hover pausa, click cierra.
 * ================================================================ */
(function () {
  'use strict';

  var MAX_VISIBLE = 3;
  var DEFAULT_DURATION = 4000;

  var stack = null;
  var queue = [];

  function ensureStack() {
    if (stack && document.body.contains(stack)) return stack;
    stack = document.createElement('div');
    stack.className = 'ed-toast-stack';
    stack.setAttribute('aria-live', 'polite');
    stack.setAttribute('aria-atomic', 'false');
    document.body.appendChild(stack);
    return stack;
  }

  var ICONS = {
    success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l3 3 5-6"/></svg>',
    error:   '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6M15 9l-6 6"/></svg>',
    info:    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><circle cx="12" cy="8" r="0.8" fill="currentColor"/></svg>',
    warning: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5"/><circle cx="12" cy="18" r="0.7" fill="currentColor"/></svg>'
  };

  function render(spec) {
    var el = document.createElement('div');
    el.className = 'ed-toast ed-toast--' + spec.type;
    el.setAttribute('role', spec.type === 'error' ? 'alert' : 'status');
    el.innerHTML =
      '<span class="ed-toast__icon">' + (ICONS[spec.type] || ICONS.info) + '</span>' +
      '<span class="ed-toast__msg"></span>' +
      (spec.dismissible !== false
        ? '<button type="button" class="ed-toast__close" aria-label="Cerrar">×</button>'
        : '');
    el.querySelector('.ed-toast__msg').textContent = spec.message;

    var timer = null;
    function startTimer() {
      clearTimeout(timer);
      timer = setTimeout(close, spec.duration || DEFAULT_DURATION);
    }
    function close() {
      clearTimeout(timer);
      el.classList.add('is-leaving');
      var remove = function () {
        if (el.parentNode) el.parentNode.removeChild(el);
        flushQueue();
      };
      var d = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 200;
      setTimeout(remove, d);
    }

    el.addEventListener('mouseenter', function () { clearTimeout(timer); });
    el.addEventListener('mouseleave', startTimer);
    el.addEventListener('click', function (e) {
      if (e.target.closest('.ed-toast__close')) close();
    });

    ensureStack().appendChild(el);
    startTimer();
    return el;
  }

  function flushQueue() {
    var s = ensureStack();
    while (queue.length && s.children.length < MAX_VISIBLE) {
      render(queue.shift());
    }
  }

  window.edToast = function (type, message, options) {
    if (typeof type !== 'string') return;
    var spec = Object.assign({ type: type, message: String(message || '') }, options || {});
    var s = ensureStack();
    if (s.children.length >= MAX_VISIBLE) {
      queue.push(spec);
      return;
    }
    render(spec);
  };
})();

/**
 * events-rsvp.js — RSVP optimista al evento.
 *
 * Hace POST con fetch(JSON) y actualiza chips de conteo en vivo. Si falla
 * revierte el estado visual. Acepta degradacion progresiva: si JS no carga
 * el form se envia normal y redirige.
 */
(function () {
  'use strict';

  function findCsrf(form) {
    const tok = form.querySelector('input[name="csrfToken"]');
    return tok ? tok.value : '';
  }

  function refreshCounts(form, counts) {
    if (!counts) return;
    Object.keys(counts).forEach(function (k) {
      const el = document.querySelector('[data-rsvp-count="' + k + '"] strong');
      if (el) el.textContent = counts[k];
    });
  }

  function setActive(form, status) {
    form.querySelectorAll('[data-rsvp-option]').forEach(function (b) {
      b.classList.toggle('is-active', b.getAttribute('data-rsvp-option') === status);
    });
  }

  function bind(form) {
    form.addEventListener('submit', function (ev) {
      const submitter = ev.submitter;
      if (!submitter || !submitter.hasAttribute('data-rsvp-option')) return;
      ev.preventDefault();
      const status = submitter.value;
      const reminder = form.querySelector('input[name="reminder"]');
      const fd = new FormData();
      fd.append('status', status);
      if (reminder && reminder.checked) fd.append('reminder', 'on');
      fd.append('csrfToken', findCsrf(form));

      const previous = form.querySelector('[data-rsvp-option].is-active');
      setActive(form, status);

      fetch(form.action, {
        method: 'POST',
        body: fd,
        headers: { 'Accept': 'application/json' },
        credentials: 'same-origin'
      }).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      }).then(function (json) {
        if (json && json.ok) {
          refreshCounts(form, json.counts);
        } else {
          throw new Error('Server error');
        }
      }).catch(function () {
        // revert
        if (previous) {
          setActive(form, previous.getAttribute('data-rsvp-option'));
        }
        if (window.edToast) {
          window.edToast('No pudimos registrar tu RSVP, reintenta.', 'error');
        }
      });
    });
  }

  function init() {
    document.querySelectorAll('[data-rsvp-form]').forEach(bind);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();

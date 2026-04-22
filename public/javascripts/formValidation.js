/* ================================================================
 * formValidation.js — Validación inline declarativa (Issue #18-D)
 *
 * Activación: <form data-validate="true">
 * Reglas por campo (data-attrs en input/textarea):
 *   data-min="N"          longitud mínima
 *   data-max="N"          longitud máxima
 *   data-pattern="regex"  patrón a cumplir
 *   data-match="otherId"  debe coincidir con otro campo
 *   data-required (o required nativo)
 * Mensajes opcionales:
 *   data-msg-min, data-msg-max, data-msg-pattern, data-msg-match, data-msg-required
 *
 * Estados visuales: .ed-field--valid · .ed-field--invalid
 * ================================================================ */
(function () {
  'use strict';

  function getField(input) {
    return input.closest('.ed-field') || input.parentElement;
  }

  function getOrCreateError(field, input) {
    var err = field.querySelector('.ed-field__error[data-live="1"]');
    if (!err) {
      err = document.createElement('div');
      err.className = 'ed-field__error';
      err.setAttribute('data-live', '1');
      err.setAttribute('aria-live', 'polite');
      input.setAttribute('aria-describedby', (input.id || '') + '__err');
      err.id = (input.id || '') + '__err';
      field.appendChild(err);
    }
    return err;
  }

  function validate(input) {
    var v = (input.value || '').trim();
    var rules = {
      required: input.hasAttribute('required') || input.dataset.required === 'true',
      min: parseInt(input.dataset.min || '0', 10),
      max: parseInt(input.dataset.max || '0', 10),
      pattern: input.dataset.pattern,
      match: input.dataset.match,
      type: input.type
    };

    if (rules.required && !v) return msg(input, 'required', 'Este campo es obligatorio.');
    if (!v) return null; // opcional vacío → sin error

    if (rules.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v))
      return msg(input, 'email', 'Email inválido.');

    if (rules.min && v.length < rules.min)
      return msg(input, 'min', 'Mínimo ' + rules.min + ' caracteres.');

    if (rules.max && v.length > rules.max)
      return msg(input, 'max', 'Máximo ' + rules.max + ' caracteres.');

    if (rules.pattern) {
      try {
        if (!new RegExp(rules.pattern).test(v))
          return msg(input, 'pattern', 'Formato inválido.');
      } catch (e) { /* regex inválido en data-attr — ignorar */ }
    }

    if (rules.match) {
      var other = document.getElementById(rules.match);
      if (other && other.value !== input.value)
        return msg(input, 'match', 'Los campos no coinciden.');
    }

    return null;
  }

  function msg(input, key, fallback) {
    return input.dataset['msg' + key.charAt(0).toUpperCase() + key.slice(1)] || fallback;
  }

  function applyState(input, errorMessage) {
    var field = getField(input);
    if (!field) return;
    field.classList.remove('ed-field--valid', 'ed-field--invalid');
    var err = getOrCreateError(field, input);
    if (errorMessage) {
      field.classList.add('ed-field--invalid');
      err.textContent = errorMessage;
      input.setAttribute('aria-invalid', 'true');
    } else {
      field.classList.add('ed-field--valid');
      err.textContent = '';
      input.setAttribute('aria-invalid', 'false');
    }
  }

  function attach(form) {
    var inputs = form.querySelectorAll('input, textarea, select');
    inputs.forEach(function (el) {
      var visited = false;
      el.addEventListener('blur', function () { visited = true; applyState(el, validate(el)); });
      el.addEventListener('input', function () {
        if (!visited) return;            // no marcar mientras escribe la primera vez
        applyState(el, validate(el));
      });
    });

    form.addEventListener('submit', function (e) {
      var firstError = null;
      inputs.forEach(function (el) {
        var err = validate(el);
        applyState(el, err);
        if (err && !firstError) firstError = el;
      });
      if (firstError) {
        e.preventDefault();
        firstError.focus();
        if (window.edToast) window.edToast('error', 'Revisa los campos marcados.');
      }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('form[data-validate="true"]').forEach(attach);
  });
})();

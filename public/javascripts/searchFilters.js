/**
 * Search filters — Editorial Reactiva
 * Issue #17-G · debounce + URL sync para /publicaciones.
 *
 * Comportamiento:
 *   - Al tipear en .ed-pub-toolbar input[name="q"], aguarda 300 ms y
 *     submitea el form, conservando la categoría activa.
 *   - Al elegir una categoría, navega normalmente (no es un combobox).
 *   - Sincroniza la URL vía history.replaceState mientras se tipea para
 *     que copiar/pegar el link conserve el query.
 *
 * No depende de un endpoint JSON: si no existe, se hace submit completo.
 * Si en el futuro se expone /publicaciones/buscar.json, este script puede
 * detectarlo (data-async-endpoint) y reemplazar la grilla sin reload.
 */
(function () {
  'use strict';

  var DEBOUNCE_MS = 300;

  function init() {
    var form  = document.querySelector('.ed-pub-toolbar form, .ed-pub-header form');
    if (!form) return;
    var input = form.querySelector('input[name="q"]');
    if (!input) return;

    var timer = null;
    var lastValue = input.value;

    function syncUrl(value) {
      try {
        var url = new URL(window.location.href);
        if (value) url.searchParams.set('q', value);
        else url.searchParams.delete('q');
        window.history.replaceState({}, '', url.toString());
      } catch (e) { /* IE / SSR */ }
    }

    input.addEventListener('input', function () {
      var v = input.value;
      if (v === lastValue) return;
      lastValue = v;
      syncUrl(v);
      if (timer) clearTimeout(timer);
      timer = setTimeout(function () { form.submit(); }, DEBOUNCE_MS);
    });

    // Si el usuario presiona Enter, submit inmediato (cancela debounce).
    form.addEventListener('submit', function () {
      if (timer) clearTimeout(timer);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

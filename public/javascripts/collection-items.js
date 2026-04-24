/* ──────────────────────────────────────────────────────────────────────────
 * collection-items.js — Issue #20
 *
 * Dos comportamientos en el detalle admin de una colección:
 *   1) Autocomplete: busca publicaciones aprobadas + artículos editoriales
 *      pickables vía /admin/collections/:id/pickable?q=...
 *   2) Reorden: botones ↑ ↓ que reescriben el orden y POSTean al endpoint
 *      /admin/collections/:id/items/reorder
 * ────────────────────────────────────────────────────────────────────────── */
(function () {
  'use strict';

  // ── 1) Autocomplete ───────────────────────────────────────────────────
  var addForm = document.querySelector('.ed-bo__items-add');
  if (addForm) {
    var search   = addForm.querySelector('#ed-pick-search');
    var hidType  = addForm.querySelector('#ed-pick-type');
    var hidId    = addForm.querySelector('#ed-pick-id');
    var submit   = addForm.querySelector('#ed-pick-submit');
    var suggest  = addForm.querySelector('#ed-pick-suggest');
    var baseUrl  = addForm.getAttribute('data-pickable-url') || '';
    var debounceTimer = null;
    var lastQuery = '';

    function clearSelection() {
      hidType.value = '';
      hidId.value   = '';
      submit.disabled = true;
    }

    function renderSuggestions(items) {
      suggest.innerHTML = '';
      if (!items || items.length === 0) {
        var li = document.createElement('li');
        li.className = 'is-empty';
        li.textContent = 'Sin resultados (solo se muestran publicaciones aprobadas y articulos publicados).';
        li.style.cursor = 'default';
        li.style.color = 'rgba(0,0,0,.5)';
        suggest.appendChild(li);
        suggest.hidden = false;
        return;
      }
      items.forEach(function (it) {
        var li = document.createElement('li');
        li.innerHTML = '<span class="type">' + (it.type === 'publication' ? 'PUB' : 'EDITORIAL') + '</span>'
                     + '<strong>' + escapeHtml(it.title) + '</strong>'
                     + ' <small>· por ' + escapeHtml(it.author || '—') + '</small>';
        li.addEventListener('mousedown', function (ev) {
          ev.preventDefault();
          search.value  = it.title;
          hidType.value = it.type;
          hidId.value   = String(it.id);
          submit.disabled = false;
          suggest.hidden = true;
        });
        suggest.appendChild(li);
      });
      suggest.hidden = false;
    }

    function fetchSuggestions(q) {
      if (!baseUrl) return;
      var url = baseUrl.replace(/q=$/, 'q=') + encodeURIComponent(q);
      fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(renderSuggestions)
        .catch(function () { suggest.hidden = true; });
    }

    search.addEventListener('input', function () {
      clearSelection();
      var q = search.value.trim();
      if (q === lastQuery) return;
      lastQuery = q;
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(function () { fetchSuggestions(q); }, 180);
    });

    search.addEventListener('focus', function () {
      // Mostrar lista inicial al hacer focus (incluye query vacia => todas las pickables)
      fetchSuggestions(search.value.trim());
    });

    document.addEventListener('click', function (ev) {
      if (!addForm.contains(ev.target)) suggest.hidden = true;
    });
  }

  // ── 2) Reorden con botones ↑ ↓ ────────────────────────────────────────
  var list = document.querySelector('.ed-bo__items-list');
  if (list) {
    var reorderUrl = list.getAttribute('data-reorder-url') || '';
    var csrfToken  = list.getAttribute('data-csrf-token') || '';

    function persistOrder() {
      var ids = Array.prototype.map.call(
        list.querySelectorAll('[data-item-row-id]'),
        function (el) { return Number(el.getAttribute('data-item-row-id')); }
      );
      var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
      if (csrfToken) headers['Csrf-Token'] = csrfToken;
      fetch(reorderUrl, {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers,
        body: JSON.stringify({ order: ids })
      }).catch(function () { /* silencioso, se reintenta al recargar */ });
    }

    list.addEventListener('click', function (ev) {
      var btn = ev.target.closest('.ed-bo__order-btn');
      if (!btn) return;
      var row = btn.closest('.ed-bo__item-row');
      if (!row) return;
      var dir = btn.getAttribute('data-dir');
      if (dir === 'up' && row.previousElementSibling) {
        list.insertBefore(row, row.previousElementSibling);
        persistOrder();
      } else if (dir === 'down' && row.nextElementSibling) {
        list.insertBefore(row.nextElementSibling, row);
        persistOrder();
      }
    });
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
})();

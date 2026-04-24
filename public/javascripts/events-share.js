/**
 * events-share.js — Web Share API + clipboard fallback para detalle de evento.
 */
(function () {
  'use strict';

  function init() {
    document.querySelectorAll('[data-share]').forEach(function (block) {
      const btn = block.querySelector('[data-share-trigger]');
      if (!btn) return;
      const title = block.getAttribute('data-share-title') || document.title;
      const url = block.getAttribute('data-share-url') || window.location.href;

      btn.addEventListener('click', function () {
        if (navigator.share) {
          navigator.share({ title: title, url: url }).catch(function () { /* user cancelled */ });
        } else if (navigator.clipboard) {
          navigator.clipboard.writeText(url).then(function () {
            if (window.edToast) window.edToast('Enlace copiado al portapapeles.', 'success');
            else btn.textContent = '¡Copiado!';
          });
        } else {
          window.prompt('Copia este enlace:', url);
        }
      });
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();

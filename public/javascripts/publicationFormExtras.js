/**
 * publicationFormExtras.js
 * ───────────────────────────────────────────────
 * Funcionalidades complementarias del editor:
 *   1. Pegar imágenes (Ctrl+V) → embebidas como markdown data-URL.
 *   2. Drag & drop de imágenes sobre el textarea.
 *   3. Botón "Volver" con confirmación si hay cambios sin guardar.
 *   4. Toast volátil para feedback (éxito / error / info).
 *
 * Funciona junto a publicationForm.js (no lo reemplaza).
 */
(function () {
  'use strict';

  var content   = document.getElementById('content');
  var fsContent = document.getElementById('fsContent');
  var toastEl   = document.getElementById('edEditorToast');
  if (!content) return;

  // ── Constantes ──────────────────────────────
  var MAX_SIZE_MB   = 4;
  var MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024;
  var ACCEPTED_TYPES = /^image\/(png|jpe?g|gif|webp|svg\+xml)$/i;

  // ── Toast ────────────────────────────────────
  var toastTimer = null;
  function toast(message, kind) {
    if (!toastEl) return;
    toastEl.textContent = message;
    toastEl.className   = 'ed-editor-toast visible' + (kind ? ' ' + kind : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toastEl.className = 'ed-editor-toast';
    }, 3200);
  }

  // ── Insertar texto en una posición del textarea ─
  function insertAtCursor(target, text) {
    if (!target) return;
    var start  = target.selectionStart;
    var end    = target.selectionEnd;
    var before = target.value.substring(0, start);
    var after  = target.value.substring(end);
    target.value = before + text + after;
    var pos = start + text.length;
    target.selectionStart = target.selectionEnd = pos;
    target.focus();
    // Disparar input para que publicationForm.js recompute stats/preview/autosave.
    target.dispatchEvent(new Event('input', { bubbles: true }));
  }

  // ── Procesar archivo de imagen → markdown data URL ─
  function handleImageFile(file, target) {
    if (!file) return;
    if (!ACCEPTED_TYPES.test(file.type)) {
      toast('Formato no soportado (usa PNG, JPG, GIF, WEBP o SVG).', 'error');
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      toast('La imagen pesa más de ' + MAX_SIZE_MB + ' MB. Comprímela antes de pegarla.', 'error');
      return;
    }

    var name = (file.name || 'imagen').replace(/\.[^.]+$/, '');
    var placeholder = '\n\n![Subiendo ' + name + '…]()\n\n';
    insertAtCursor(target, placeholder);
    toast('Procesando imagen…', 'info');

    var reader = new FileReader();
    reader.onload = function (e) {
      var dataUrl = e.target.result;
      var markdown = '![' + name + '](' + dataUrl + ')';
      // Reemplazamos el placeholder exacto (primera ocurrencia).
      target.value = target.value.replace(placeholder.trim(), markdown);
      target.dispatchEvent(new Event('input', { bubbles: true }));
      toast('Imagen incrustada (' + (file.size / 1024).toFixed(0) + ' KB).', 'success');
    };
    reader.onerror = function () {
      target.value = target.value.replace(placeholder, '');
      target.dispatchEvent(new Event('input', { bubbles: true }));
      toast('No se pudo leer la imagen.', 'error');
    };
    reader.readAsDataURL(file);
  }

  // ── Listener de paste ────────────────────────
  function bindPaste(target) {
    if (!target) return;
    target.addEventListener('paste', function (event) {
      var items = (event.clipboardData || window.clipboardData || {}).items;
      if (!items) return;
      for (var i = 0; i < items.length; i++) {
        var it = items[i];
        if (it.kind === 'file' && it.type.indexOf('image/') === 0) {
          event.preventDefault();
          handleImageFile(it.getAsFile(), target);
          return;
        }
      }
    });
  }

  // ── Listener de drag & drop ──────────────────
  function bindDrop(target) {
    if (!target) return;
    ['dragenter', 'dragover'].forEach(function (ev) {
      target.addEventListener(ev, function (e) {
        if (e.dataTransfer && Array.prototype.some.call(e.dataTransfer.types || [], function (t) { return t === 'Files'; })) {
          e.preventDefault();
          target.classList.add('is-dropping');
        }
      });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      target.addEventListener(ev, function () { target.classList.remove('is-dropping'); });
    });
    target.addEventListener('drop', function (e) {
      var files = e.dataTransfer && e.dataTransfer.files;
      if (!files || files.length === 0) return;
      e.preventDefault();
      Array.prototype.forEach.call(files, function (f) { handleImageFile(f, target); });
    });
  }

  bindPaste(content);
  bindDrop(content);
  if (fsContent) { bindPaste(fsContent); bindDrop(fsContent); }

  // ── Botón "Volver" con guard de cambios ──────
  var backBtn = document.querySelector('.ed-back-btn');
  if (backBtn) {
    backBtn.addEventListener('click', function (e) {
      // hasUnsavedChanges está scope-encerrado en publicationForm.js,
      // pero el módulo le pone window.onbeforeunload — usamos un proxy:
      var dirty = !!(window.onbeforeunload && (function () {
        try { return window.onbeforeunload({}) != null; } catch (_) { return false; }
      })());
      if (dirty) {
        var ok = window.confirm('Tenés cambios sin guardar. ¿Salir igual? Tu borrador queda guardado localmente.');
        if (!ok) { e.preventDefault(); return; }
        // Limpiamos el guard para que no vuelva a preguntar.
        window.onbeforeunload = null;
      }
    });
  }

  // Aceptar también drop sobre todo el shell sin "tragarse" otros eventos.
  var shell = document.querySelector('.ed-editor-shell');
  if (shell) {
    shell.addEventListener('dragover', function (e) {
      if (e.dataTransfer && Array.prototype.some.call(e.dataTransfer.types || [], function (t) { return t === 'Files'; })) {
        e.preventDefault();
      }
    });
    shell.addEventListener('drop', function (e) {
      // Si el drop ocurre fuera del textarea, lo redirigimos al contenido.
      if (e.target === content || e.target === fsContent) return;
      var files = e.dataTransfer && e.dataTransfer.files;
      if (!files || files.length === 0) return;
      e.preventDefault();
      Array.prototype.forEach.call(files, function (f) { handleImageFile(f, content); });
    });
  }

  // ── Sticky toolbar: marca .is-stuck cuando se pega al top ──
  var toolbar = document.querySelector('.ed-md-toolbar');
  if (toolbar && 'IntersectionObserver' in window) {
    // Sentinel invisible justo encima de la toolbar.
    var sentinel = document.createElement('div');
    sentinel.setAttribute('aria-hidden', 'true');
    sentinel.style.cssText = 'height:1px;margin-bottom:-1px;';
    toolbar.parentNode.insertBefore(sentinel, toolbar);
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        toolbar.classList.toggle('is-stuck', !entry.isIntersecting);
      });
    }, { threshold: [0], rootMargin: '0px 0px 0px 0px' });
    io.observe(sentinel);
  }
})();

/* ============================================
 * Embeds (Claude artifacts, YouTube, Vimeo, CodePen, iframes genéricos)
 * Sintaxis en markdown:   ::: embed <URL> :::
 * Se renderiza como iframe en el preview.
 * ============================================ */
(function () {
  'use strict';

  var content     = document.getElementById('content');
  var previewPane = document.getElementById('previewPane');
  var fsContent   = document.getElementById('fsContent');
  var fsPreview   = document.getElementById('fsPreview');
  if (!content) return;

  // ── 1. Botón de toolbar: pide URL e inserta el marcador ──
  window.insertEmbed = function () {
    var url = window.prompt('Pegá la URL a embebir (Claude artifact, YouTube, Vimeo, CodePen, etc.):');
    if (!url) return;
    url = url.trim();
    if (!/^https?:\/\//i.test(url)) {
      window.alert('La URL debe empezar con http:// o https://');
      return;
    }
    var snippet = '\n\n::: embed ' + url + ' :::\n\n';
    var target  = document.activeElement === fsContent ? fsContent : content;
    var start   = target.selectionStart;
    var end     = target.selectionEnd;
    target.value = target.value.substring(0, start) + snippet + target.value.substring(end);
    var pos = start + snippet.length;
    target.selectionStart = target.selectionEnd = pos;
    target.focus();
    target.dispatchEvent(new Event('input', { bubbles: true }));
  };

  // ── 2. Conversor URL → iframe ──
  function urlToEmbed(url) {
    // YouTube
    var yt = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([\w-]{11})/);
    if (yt) return iframe('https://www.youtube.com/embed/' + yt[1], '16/9');
    // Vimeo
    var vm = url.match(/vimeo\.com\/(?:video\/)?(\d+)/);
    if (vm) return iframe('https://player.vimeo.com/video/' + vm[1], '16/9');
    // CodePen
    var cp = url.match(/codepen\.io\/([^/]+)\/pen\/(\w+)/);
    if (cp) return iframe('https://codepen.io/' + cp[1] + '/embed/' + cp[2] + '?default-tab=result', '4/3');
    // Claude artifacts (mismo URL ya sirve HTML interactivo)
    if (/claude\.ai\/public\/artifacts\//i.test(url)) {
      return iframe(url, '4/3', /*sandbox*/ 'allow-scripts allow-same-origin allow-forms allow-popups');
    }
    // Genérico: cualquier https → iframe sandboxed
    return iframe(url, '16/9', 'allow-scripts allow-same-origin');
  }

  function iframe(src, ratio, sandbox) {
    var wrap = document.createElement('div');
    wrap.className = 'ed-embed';
    wrap.style.aspectRatio = ratio.replace('/', ' / ');
    var i = document.createElement('iframe');
    i.src = src;
    i.loading = 'lazy';
    i.referrerPolicy = 'no-referrer';
    i.allow = 'fullscreen; clipboard-read; clipboard-write';
    if (sandbox) i.sandbox = sandbox;
    wrap.appendChild(i);
    var caption = document.createElement('a');
    caption.href = src;
    caption.target = '_blank';
    caption.rel = 'noopener';
    caption.className = 'ed-embed__caption';
    caption.textContent = '↗ Abrir en nueva pestaña';
    wrap.appendChild(caption);
    return wrap;
  }

  // ── 3. Post-procesar el preview: detectar marcadores y reemplazar por iframes ──
  // El renderer de markdownToHtml escapa < y >, así que el marcador queda como
  //   <p>::: embed URL :::</p>  (texto plano dentro de un <p>).
  var EMBED_RE = /^:::\s*embed\s+(\S+)\s*:::$/i;

  function processEmbeds(root) {
    if (!root) return;
    var paragraphs = root.querySelectorAll('p');
    paragraphs.forEach(function (p) {
      var text = (p.textContent || '').trim();
      var m = text.match(EMBED_RE);
      if (m) {
        var node = urlToEmbed(m[1]);
        p.parentNode.replaceChild(node, p);
      }
    });
  }

  function observe(targetPane) {
    if (!targetPane) return;
    // Procesar inmediatamente y luego en cada mutación.
    processEmbeds(targetPane);
    var mo = new MutationObserver(function () { processEmbeds(targetPane); });
    mo.observe(targetPane, { childList: true, subtree: false });
  }

  observe(previewPane);
  observe(fsPreview);
})();

/**
 * ============================================
 * Publication Form — Interactive Module
 * Manejo interactivo del formulario de publicaciones
 * ============================================
 *
 * Features:
 *  1.  Title — Character counter & live preview
 *  2.  Excerpt — Character counter
 *  3.  Content — Stats, preview, TOC updates
 *  4.  Markdown → HTML renderer
 *  5.  Preview panel toggle
 *  6.  Table of contents generator
 *  7.  Markdown toolbar insert
 *  8.  Fullscreen editor
 *  9.  Tag chips interactive input
 * 10.  Image URL live preview
 * 11.  Progress bar
 * 12.  Auto-save draft (localStorage)
 * 13.  Unsaved changes warning
 * 14.  Collapsible form sections
 * 15.  Category → auto-suggest tags
 */
(function () {
    'use strict';

    // ── DOM REFERENCES ──────────────────────────
    var titleInput       = document.getElementById('title');
    var titleCount       = document.getElementById('titleCount');
    var titlePreview     = document.getElementById('titlePreview');
    var titleIndicator   = document.getElementById('titleIndicator');
    var excerptInput     = document.getElementById('excerpt');
    var excerptCount     = document.getElementById('excerptCount');
    var excerptIndicator = document.getElementById('excerptIndicator');
    var contentInput     = document.getElementById('content');
    var contentCount     = document.getElementById('contentCount');
    var contentIndicator = document.getElementById('contentIndicator');
    var categoryInput    = document.getElementById('category');
    var tagsHidden       = document.getElementById('tags');
    var coverInput       = document.getElementById('coverImage');
    var previewPane      = document.getElementById('previewPane');
    var previewToggle    = document.getElementById('previewToggle');
    var tocPanel         = document.getElementById('tocPanel');
    var tocToggle        = document.getElementById('tocToggle');
    var tocList          = document.getElementById('tocList');
    var progressFill     = document.getElementById('progressFill');
    var progressText     = document.getElementById('progressText');

    // Bail out if the form isn't present on the page
    if (!titleInput || !contentInput) return;

    // ── STATE ───────────────────────────────────
    var currentTags       = [];
    var hasUnsavedChanges = false;
    var autosaveTimer     = null;
    var AUTOSAVE_KEY      = 'pub_draft_' + (window.location.pathname.indexOf('/edit') > -1
                                            ? window.location.pathname : 'new');

    // ═══════════════════════════════════════════
    // 1. TITLE — Character Counter & Preview
    // ═══════════════════════════════════════════
    function updateTitleCount() {
        var count = titleInput.value.length;
        titleCount.textContent = count;
        titlePreview.textContent = titleInput.value || 'Tu título aparecerá aquí';

        if (count < 5) {
            titleIndicator.textContent = '⚠️ Muy corto - mínimo 5 caracteres';
            titleIndicator.className   = 'character-indicator error';
        } else if (count < 30) {
            titleIndicator.textContent = '✓ Corto - considera ser más descriptivo';
            titleIndicator.className   = 'character-indicator warning';
        } else if (count > 180) {
            titleIndicator.textContent = '⚠️ Muy largo - considera acortar';
            titleIndicator.className   = 'character-indicator warning';
        } else {
            titleIndicator.textContent    = '✓ Longitud ideal';
            titleIndicator.className      = 'character-indicator';
            titleIndicator.style.color    = '#10b981';
        }
        markDirty();
    }

    titleInput.addEventListener('input', updateTitleCount);
    updateTitleCount();

    // ═══════════════════════════════════════════
    // 2. EXCERPT — Counter
    // ═══════════════════════════════════════════
    function updateExcerptCount() {
        var count = excerptInput.value.length;
        excerptCount.textContent = count;

        if (count === 0) {
            excerptIndicator.textContent = '';
        } else if (count < 50) {
            excerptIndicator.textContent = '✓ Descripción breve';
            excerptIndicator.className   = 'character-indicator';
        } else if (count > 480) {
            excerptIndicator.textContent = '⚠️ Acercándose al límite';
            excerptIndicator.className   = 'character-indicator warning';
        } else {
            excerptIndicator.textContent    = '✓ Buen extracto';
            excerptIndicator.className      = 'character-indicator';
            excerptIndicator.style.color    = '#10b981';
        }
        markDirty();
    }

    excerptInput.addEventListener('input', updateExcerptCount);
    updateExcerptCount();

    // ═══════════════════════════════════════════
    // 3. CONTENT — Stats, Preview, TOC
    // ═══════════════════════════════════════════
    function updateContentStats() {
        var text     = contentInput.value;
        var chars    = text.length;
        var words    = text.trim() ? text.trim().split(/\s+/).length : 0;
        var lines    = text.split('\n').length;
        var readMins = Math.max(1, Math.ceil(words / 200));

        contentCount.textContent = chars.toLocaleString();
        document.getElementById('wordCount').textContent  = words.toLocaleString();
        document.getElementById('readTime').textContent   = readMins + ' min';
        document.getElementById('charCount').textContent  = chars.toLocaleString();
        document.getElementById('lineCount').textContent  = lines;

        if (chars < 50) {
            contentIndicator.textContent = '⚠️ Muy corto - mínimo 50 caracteres';
            contentIndicator.className   = 'character-indicator error';
        } else if (chars < 200) {
            contentIndicator.textContent = '✓ Contenido mínimo aceptado';
            contentIndicator.className   = 'character-indicator warning';
        } else if (chars > 5000) {
            contentIndicator.textContent    = '✓ Excelente cantidad de contenido';
            contentIndicator.className      = 'character-indicator';
            contentIndicator.style.color    = '#10b981';
        } else {
            contentIndicator.textContent    = '✓ Buen contenido';
            contentIndicator.className      = 'character-indicator';
            contentIndicator.style.color    = '#10b981';
        }

        if (previewPane.classList.contains('visible')) renderPreview();
        if (tocPanel.classList.contains('visible'))    renderTOC();

        markDirty();
    }

    contentInput.addEventListener('input', updateContentStats);
    updateContentStats();

    // ═══════════════════════════════════════════
    // 4. MARKDOWN → HTML Renderer (simple)
    // ═══════════════════════════════════════════
    function markdownToHtml(md) {
        if (!md || !md.trim()) {
            return '<div class="preview-empty">Escribe contenido para ver la vista previa...</div>';
        }

        var html = md;

        // Escape HTML
        html = html.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

        // Code blocks
        html = html.replace(/```(\w*)\n([\s\S]*?)```/g, function (_, lang, code) {
            return '<pre><code class="language-' + (lang || 'text') + '">' + code.trim() + '</code></pre>';
        });

        // Inline code
        html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

        // Headers
        html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        html = html.replace(/^## (.+)$/gm,  '<h2>$1</h2>');
        html = html.replace(/^# (.+)$/gm,   '<h1>$1</h1>');

        // Bold, Italic, Strikethrough
        html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
        html = html.replace(/\*\*(.+?)\*\*/g,     '<strong>$1</strong>');
        html = html.replace(/\*(.+?)\*/g,          '<em>$1</em>');
        html = html.replace(/~~(.+?)~~/g,          '<del>$1</del>');

        // Blockquotes
        html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>');

        // Horizontal rule
        html = html.replace(/^---$/gm, '<hr>');

        // Images & Links
        html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" />');
        html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g,  '<a href="$2" target="_blank" rel="noopener">$1</a>');

        // Lists
        html = html.replace(/^[\-\*] (.+)$/gm, '<li>$1</li>');
        html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');
        html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');

        // Paragraphs
        html = html.replace(/\n\n+/g, '</p><p>');
        html = '<p>' + html + '</p>';

        // Clean up empty / mis-nested paragraphs
        html = html.replace(/<p>\s*<\/p>/g, '');
        html = html.replace(/<p>\s*(<h[1-3]>)/g,        '$1');
        html = html.replace(/(<\/h[1-3]>)\s*<\/p>/g,    '$1');
        html = html.replace(/<p>\s*(<pre>)/g,            '$1');
        html = html.replace(/(<\/pre>)\s*<\/p>/g,        '$1');
        html = html.replace(/<p>\s*(<ul>)/g,             '$1');
        html = html.replace(/(<\/ul>)\s*<\/p>/g,         '$1');
        html = html.replace(/<p>\s*(<blockquote>)/g,     '$1');
        html = html.replace(/(<\/blockquote>)\s*<\/p>/g, '$1');
        html = html.replace(/<p>\s*(<hr>)/g,             '$1');
        html = html.replace(/(<hr>)\s*<\/p>/g,           '$1');

        return html;
    }

    function renderPreview() {
        previewPane.innerHTML = markdownToHtml(contentInput.value);
    }

    // ═══════════════════════════════════════════
    // 5. TOGGLE PREVIEW PANEL
    // ═══════════════════════════════════════════
    window.togglePreview = function () {
        var isVisible = previewPane.classList.toggle('visible');
        previewToggle.classList.toggle('active', isVisible);
        if (isVisible) renderPreview();
    };

    // ═══════════════════════════════════════════
    // 6. TABLE OF CONTENTS
    // ═══════════════════════════════════════════
    function renderTOC() {
        var text        = contentInput.value;
        var headerRegex = /^(#{1,3}) (.+)$/gm;
        var headers     = [];
        var match;

        while ((match = headerRegex.exec(text)) !== null) {
            headers.push({ level: match[1].length, text: match[2] });
        }

        if (headers.length === 0) {
            tocList.innerHTML = '<li class="toc-empty">Agrega encabezados (#, ##, ###) para generar la tabla de contenidos</li>';
        } else {
            tocList.innerHTML = headers.map(function (h) {
                return '<li class="level-' + h.level + '">' + '\u2014'.repeat(h.level - 1) + ' ' + h.text + '</li>';
            }).join('');
        }
    }

    window.toggleTOC = function () {
        var isVisible = tocPanel.classList.toggle('visible');
        tocToggle.classList.toggle('active', isVisible);
        if (isVisible) renderTOC();
    };

    // ═══════════════════════════════════════════
    // 7. MARKDOWN TOOLBAR — Insert Formatting
    // ═══════════════════════════════════════════
    window.insertMd = function (type) {
        var ta       = contentInput;
        var start    = ta.selectionStart;
        var end      = ta.selectionEnd;
        var selected = ta.value.substring(start, end);
        var before   = '';
        var after    = '';
        var insert   = '';

        switch (type) {
            case 'bold':
                before = '**'; after = '**';
                insert = selected || 'texto en negrita';
                break;
            case 'italic':
                before = '*'; after = '*';
                insert = selected || 'texto en cursiva';
                break;
            case 'strike':
                before = '~~'; after = '~~';
                insert = selected || 'texto tachado';
                break;
            case 'h1':
                before = '\n# '; after = '\n';
                insert = selected || 'Encabezado 1';
                break;
            case 'h2':
                before = '\n## '; after = '\n';
                insert = selected || 'Encabezado 2';
                break;
            case 'h3':
                before = '\n### '; after = '\n';
                insert = selected || 'Encabezado 3';
                break;
            case 'ul':
                before = '\n';
                insert = (selected || 'Elemento 1\nElemento 2\nElemento 3')
                    .split('\n').map(function (l) { return '- ' + l.trim(); }).join('\n');
                after = '\n';
                break;
            case 'ol':
                before = '\n';
                insert = (selected || 'Primer paso\nSegundo paso\nTercer paso')
                    .split('\n').map(function (l, i) { return (i + 1) + '. ' + l.trim(); }).join('\n');
                after = '\n';
                break;
            case 'quote':
                before = '\n> ';
                insert = selected || 'Cita textual aquí';
                after  = '\n';
                break;
            case 'code':
                before = '`'; after = '`';
                insert = selected || 'código';
                break;
            case 'codeblock':
                before = '\n```scala\n';
                insert = selected || '// Tu código aquí\nval x = 42';
                after  = '\n```\n';
                break;
            case 'link':
                before = '[';
                insert = selected || 'texto del enlace';
                after  = '](https://url)';
                break;
            case 'image':
                before = '![';
                insert = selected || 'descripción';
                after  = '](https://url-de-imagen)';
                break;
            case 'hr':
                before = '\n'; insert = '---'; after = '\n';
                break;
            case 'table':
                before = '\n';
                insert = '| Columna 1 | Columna 2 | Columna 3 |\n' +
                         '|-----------|-----------|----------|\n'  +
                         '| Celda 1   | Celda 2   | Celda 3  |';
                after  = '\n';
                break;
        }

        var replacement = before + insert + after;
        ta.setRangeText(replacement, start, end, 'end');
        ta.focus();

        var newStart = start + before.length;
        var newEnd   = newStart + insert.length;
        ta.setSelectionRange(newStart, newEnd);
        updateContentStats();
    };

    // Keyboard shortcuts for content editor
    contentInput.addEventListener('keydown', function (e) {
        if (e.ctrlKey || e.metaKey) {
            if (e.key === 'b') { e.preventDefault(); window.insertMd('bold'); }
            if (e.key === 'i') { e.preventDefault(); window.insertMd('italic'); }
            if (e.shiftKey && e.key === 'F') { e.preventDefault(); window.openFullscreen(); }
        }
        if (e.key === 'Tab') {
            e.preventDefault();
            var s = this.selectionStart;
            this.setRangeText('    ', s, this.selectionEnd, 'end');
            updateContentStats();
        }
    });

    // ═══════════════════════════════════════════
    // 8. FULLSCREEN EDITOR
    // ═══════════════════════════════════════════
    var fsOverlay = document.getElementById('fullscreenOverlay');
    var fsContent = document.getElementById('fsContent');
    var fsPreview = document.getElementById('fsPreview');
    var fsToggle  = document.getElementById('fsPreviewToggle');

    function updateFullscreenStats() {
        var text  = fsContent.value;
        var words = text.trim() ? text.trim().split(/\s+/).length : 0;
        document.getElementById('fsWordCount').textContent = words;
        document.getElementById('fsReadTime').textContent  = Math.max(1, Math.ceil(words / 200));
        document.getElementById('fsCharCount').textContent = text.length.toLocaleString();
        if (fsPreview.classList.contains('visible')) {
            fsPreview.innerHTML = markdownToHtml(text);
        }
    }

    window.openFullscreen = function () {
        fsContent.value = contentInput.value;
        fsOverlay.classList.add('active');
        document.body.style.overflow = 'hidden';
        fsContent.focus();
        updateFullscreenStats();
    };

    window.closeFullscreen = function () {
        contentInput.value = fsContent.value;
        fsOverlay.classList.remove('active');
        document.body.style.overflow = '';
        updateContentStats();
    };

    window.toggleFullscreenPreview = function () {
        var isVisible = fsPreview.classList.toggle('visible');
        fsToggle.classList.toggle('active', isVisible);
        if (isVisible) fsPreview.innerHTML = markdownToHtml(fsContent.value);
    };

    fsContent.addEventListener('input', updateFullscreenStats);
    fsContent.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') { e.preventDefault(); window.closeFullscreen(); }
        if (e.ctrlKey && e.key === 'b') { e.preventDefault(); }
        if (e.key === 'Tab') {
            e.preventDefault();
            var s = this.selectionStart;
            this.setRangeText('    ', s, this.selectionEnd, 'end');
            updateFullscreenStats();
        }
    });

    // Global keyboard shortcuts
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && fsOverlay.classList.contains('active')) {
            window.closeFullscreen();
        }
        if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'F') {
            e.preventDefault();
            if (fsOverlay.classList.contains('active')) window.closeFullscreen();
            else window.openFullscreen();
        }
    });

    // ═══════════════════════════════════════════
    // 9. INTERACTIVE TAG CHIPS
    // ═══════════════════════════════════════════
    var tagChipsContainer = document.getElementById('tagChips');
    var tagInputInline    = document.getElementById('tagInputInline');
    var tagCountBadge     = document.getElementById('tagCountBadge');
    var MAX_TAGS          = 7;

    function createTagChip(tag) {
        var chip = document.createElement('span');
        chip.className   = 'tag-chip';
        chip.dataset.tag = tag;
        chip.innerHTML   = tag +
            ' <button type="button" class="tag-chip-remove" onclick="removeTag(\'' +
            tag.replace(/'/g, "\\'") + '\')" aria-label="Eliminar">&times;</button>';
        tagChipsContainer.insertBefore(chip, tagInputInline);
    }

    function syncTags() {
        tagsHidden.value = currentTags.join(', ');
        var label = document.getElementById('tagsLabel');
        label.textContent = '(' + currentTags.length + ')';

        if (currentTags.length > MAX_TAGS) {
            tagCountBadge.textContent = '⚠️ Demasiadas etiquetas (' + currentTags.length + '/' + MAX_TAGS + ')';
            tagCountBadge.className   = 'tag-count-badge error';
        } else if (currentTags.length >= 5) {
            tagCountBadge.textContent = '✓ ' + currentTags.length + '/' + MAX_TAGS + ' etiquetas';
            tagCountBadge.className   = 'tag-count-badge warning';
        } else if (currentTags.length > 0) {
            tagCountBadge.textContent = currentTags.length + '/' + MAX_TAGS + ' etiquetas';
            tagCountBadge.className   = 'tag-count-badge';
        } else {
            tagCountBadge.textContent = '';
        }

        document.querySelectorAll('.tag-suggestion').forEach(function (el) {
            el.classList.toggle('used', currentTags.indexOf(el.dataset.tag) > -1);
        });

        markDirty();
        updateProgress();
    }

    function addTag(tag) {
        tag = tag.trim().toLowerCase().replace(/[^a-záéíóúñ0-9\-_]/g, '');
        if (!tag || currentTags.indexOf(tag) > -1 || currentTags.length >= MAX_TAGS) return;
        currentTags.push(tag);
        createTagChip(tag);
        syncTags();
    }

    window.removeTag = function (tag) {
        currentTags = currentTags.filter(function (t) { return t !== tag; });
        var chip = tagChipsContainer.querySelector('[data-tag="' + tag + '"]');
        if (chip) {
            chip.style.transform = 'scale(0.8)';
            chip.style.opacity   = '0';
            setTimeout(function () { chip.remove(); }, 150);
        }
        syncTags();
    };

    window.addTagFromSuggestion = function (el) {
        addTag(el.dataset.tag);
        tagInputInline.focus();
    };

    tagInputInline.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            addTag(this.value);
            this.value = '';
        }
        if (e.key === 'Backspace' && !this.value && currentTags.length > 0) {
            window.removeTag(currentTags[currentTags.length - 1]);
        }
    });

    tagInputInline.addEventListener('blur', function () {
        if (this.value.trim()) {
            addTag(this.value);
            this.value = '';
        }
    });

    // Initialize tags from existing hidden input value
    (function initTags() {
        var existing = tagsHidden.value;
        if (existing && existing.trim()) {
            currentTags = existing.split(',').map(function (t) { return t.trim(); }).filter(Boolean);
            currentTags.forEach(createTagChip);
            syncTags();
        }
    })();

    // ═══════════════════════════════════════════
    // 10. IMAGE URL LIVE PREVIEW
    // ═══════════════════════════════════════════
    var imageContainer  = document.getElementById('imagePreviewContainer');
    var imagePreviewImg = document.getElementById('imagePreviewImg');
    var imageLoading    = document.getElementById('imagePreviewLoading');
    var imageError      = document.getElementById('imagePreviewError');
    var imageDims       = document.getElementById('imageDimensions');
    var imageDebounce   = null;

    function previewImage(url) {
        if (!url || !url.match(/^https?:\/\/.+/i)) {
            imageContainer.classList.remove('visible');
            return;
        }
        imageContainer.classList.add('visible');
        imagePreviewImg.style.display = 'none';
        imageLoading.style.display    = 'inline';
        imageError.style.display      = 'none';
        imageDims.textContent         = '';

        var img     = new Image();
        img.onload  = function () {
            imagePreviewImg.src           = url;
            imagePreviewImg.style.display = 'block';
            imageLoading.style.display    = 'none';
            imageDims.textContent         = img.naturalWidth + ' × ' + img.naturalHeight + 'px';
        };
        img.onerror = function () {
            imageLoading.style.display = 'none';
            imageError.style.display   = 'flex';
        };
        img.src = url;
    }

    coverInput.addEventListener('input', function () {
        clearTimeout(imageDebounce);
        imageDebounce = setTimeout(function () {
            previewImage(coverInput.value);
        }, 600);
        markDirty();
    });

    if (coverInput.value) previewImage(coverInput.value);

    // ═══════════════════════════════════════════
    // 11. PROGRESS BAR
    // ═══════════════════════════════════════════
    function updateProgress() {
        var score   = 0;
        var checks  = [
            titleInput.value.length >= 5,
            categoryInput.value !== '',
            contentInput.value.length >= 50,
            excerptInput.value.length > 0,
            currentTags.length > 0,
            coverInput.value.length > 0
        ];
        var weights = [25, 15, 35, 10, 10, 5];

        checks.forEach(function (ok, i) { if (ok) score += weights[i]; });

        progressFill.style.width = score + '%';
        progressText.textContent = score + '%';

        if (score < 40) {
            progressFill.style.background = 'linear-gradient(90deg, #f87171, #fb923c)';
        } else if (score < 75) {
            progressFill.style.background = 'linear-gradient(90deg, #fb923c, #fbbf24)';
        } else {
            progressFill.style.background = 'linear-gradient(90deg, #34d399, #10b981)';
        }
    }

    [titleInput, contentInput, excerptInput, coverInput].forEach(function (el) {
        el.addEventListener('input', updateProgress);
    });
    categoryInput.addEventListener('change', updateProgress);
    updateProgress();

    // ═══════════════════════════════════════════
    // 12. AUTO-SAVE DRAFT (localStorage)
    // ═══════════════════════════════════════════
    var autosaveIndicator = document.getElementById('autosaveIndicator');
    var autosaveTextEl    = document.getElementById('autosaveText');
    var restoreDraftBtn   = document.getElementById('restoreDraftBtn');

    function saveDraft() {
        var draft = {
            title:      titleInput.value,
            category:   categoryInput.value,
            tags:       currentTags.join(', '),
            excerpt:    excerptInput.value,
            content:    contentInput.value,
            coverImage: coverInput.value,
            savedAt:    new Date().toISOString()
        };
        try {
            localStorage.setItem(AUTOSAVE_KEY, JSON.stringify(draft));
            autosaveIndicator.className = 'autosave-indicator saved';
            autosaveTextEl.textContent  = '✓ Borrador guardado · ' + new Date().toLocaleTimeString();
            setTimeout(function () {
                autosaveIndicator.className = 'autosave-indicator';
            }, 3000);
        } catch (e) { /* localStorage full or unavailable */ }
    }

    function markDirty() {
        hasUnsavedChanges = true;
        clearTimeout(autosaveTimer);
        autosaveTimer = setTimeout(function () {
            autosaveIndicator.className = 'autosave-indicator saving';
            autosaveTextEl.textContent  = 'Guardando borrador...';
            setTimeout(saveDraft, 500);
        }, 2000);
    }

    window.restoreDraft = function () {
        try {
            var saved = JSON.parse(localStorage.getItem(AUTOSAVE_KEY));
            if (!saved) return;

            titleInput.value    = saved.title      || '';
            categoryInput.value = saved.category   || '';
            excerptInput.value  = saved.excerpt    || '';
            contentInput.value  = saved.content    || '';
            coverInput.value    = saved.coverImage || '';

            // Restore tags
            currentTags = [];
            tagChipsContainer.querySelectorAll('.tag-chip').forEach(function (c) { c.remove(); });
            if (saved.tags) {
                saved.tags.split(',').map(function (t) { return t.trim(); }).filter(Boolean).forEach(function (tag) {
                    currentTags.push(tag);
                    createTagChip(tag);
                });
            }
            syncTags();
            updateTitleCount();
            updateExcerptCount();
            updateContentStats();
            updateProgress();
            if (coverInput.value) previewImage(coverInput.value);
            restoreDraftBtn.style.display = 'none';
            autosaveTextEl.textContent    = '✓ Borrador restaurado';
        } catch (e) { /* ignore */ }
    };

    window.clearDraft = function () {
        try { localStorage.removeItem(AUTOSAVE_KEY); } catch (e) { /* ignore */ }
        restoreDraftBtn.style.display = 'none';
        autosaveTextEl.textContent    = 'Borrador eliminado';
        setTimeout(function () { autosaveTextEl.textContent = 'Auto-guardado activo'; }, 2000);
    };

    // Check for existing draft on load
    try {
        var existingDraft = localStorage.getItem(AUTOSAVE_KEY);
        if (existingDraft) {
            var draft     = JSON.parse(existingDraft);
            var savedDate = new Date(draft.savedAt);
            var isOld     = (Date.now() - savedDate.getTime()) > 7 * 24 * 60 * 60 * 1000;
            if (!isOld && draft.content && draft.content.length > 10) {
                restoreDraftBtn.style.display = 'inline-block';
                autosaveTextEl.textContent    = 'Borrador encontrado · ' +
                    savedDate.toLocaleDateString() + ' ' + savedDate.toLocaleTimeString();
            }
        }
    } catch (e) { /* ignore */ }

    // ═══════════════════════════════════════════
    // 13. UNSAVED CHANGES WARNING
    // ═══════════════════════════════════════════
    window.addEventListener('beforeunload', function (e) {
        if (hasUnsavedChanges) {
            e.preventDefault();
            e.returnValue = 'Tienes cambios sin guardar. ¿Seguro que quieres salir?';
        }
    });

    document.querySelector('.publication-form').addEventListener('submit', function () {
        hasUnsavedChanges = false;
        window.clearDraft();
    });

    // ═══════════════════════════════════════════
    // 14. COLLAPSIBLE SECTIONS
    // ═══════════════════════════════════════════
    document.querySelectorAll('.form-section-title').forEach(function (title) {
        var icon       = document.createElement('span');
        icon.className = 'collapse-icon';
        icon.textContent = '▼';
        title.appendChild(icon);

        var section = title.parentElement;
        var body    = document.createElement('div');
        body.className = 'form-section-body';

        while (section.children.length > 1) {
            body.appendChild(section.children[1]);
        }
        section.appendChild(body);

        title.addEventListener('click', function () {
            section.classList.toggle('collapsed');
        });
    });

    // ═══════════════════════════════════════════
    // 15. CATEGORY → AUTO-SUGGEST TAGS
    // ═══════════════════════════════════════════
    var categoryTagMap = {
        'Scala':            ['scala', 'functional', 'jvm'],
        'Akka':             ['akka', 'actors', 'reactive'],
        'Play Framework':   ['play-framework', 'scala', 'web'],
        'Reactive Streams': ['reactive', 'streams', 'async'],
        'Arquitectura':     ['arquitectura', 'patterns', 'design'],
        'Patterns':         ['patterns', 'design', 'scala'],
        'Testing':          ['testing', 'tdd', 'scala'],
        'DevOps':           ['devops', 'docker', 'ci-cd']
    };

    categoryInput.addEventListener('change', function () {
        var suggestions = categoryTagMap[this.value];
        if (suggestions && currentTags.length === 0) {
            var first = suggestions.find(function (s) { return currentTags.indexOf(s) === -1; });
            if (first) addTag(first);
        }
        updateProgress();
    });

})();

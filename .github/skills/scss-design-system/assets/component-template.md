// ============================================
// <NombreComponente> — Editorial Reactiva
//
// Nivel: shared/ | tags/ | sections/ | <dominio>/
// Importar en main.scss en la capa correspondiente.
// ============================================

// ── Placeholder (base estructural sin output directo) ─────────────────────
// Usar @extend en variantes para compartir propiedades sin duplicar CSS.

%ed-<nombre>-base {
  // Propiedades estructurales (display, position, box model)
  display: block;
  box-sizing: border-box;

  // Tipografía
  font-family: var(--font-body);
  font-size:   1rem;
  line-height: 1.5;
  color:       var(--color-text-primary);

  // Focus accesible — SIEMPRE en elementos interactivos
  @include focus-ring;
}

// ── Variante principal ────────────────────────────────────────────────────

.ed-<nombre> {
  @extend %ed-<nombre>-base;

  // Colores: SIEMPRE tokens semánticos, nunca primitivos
  background: var(--color-surface-raised);
  border:     1px solid var(--color-border-subtle);

  // Espaciado: usar variables $space-* o CSS vars --space-*
  padding: $space-5 $space-6;

  // Animaciones: envolver en motion-safe para respetar a11y
  @include motion-safe {
    transition: box-shadow 0.2s, transform 0.15s;
    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }
  }

  // Responsive — siempre al final del bloque del componente
  @include respond-to(md) {
    padding: $space-4;
  }
}

// ── Variante acento ───────────────────────────────────────────────────────

.ed-<nombre>--accent {
  @extend %ed-<nombre>-base;
  background: var(--color-accent);
  color:      var(--color-on-accent);
  border:     none;

  &:hover { background: var(--color-accent-hover); }
}

// ── Variante ghost ────────────────────────────────────────────────────────

.ed-<nombre>--ghost {
  @extend %ed-<nombre>-base;
  background: transparent;
  color:      var(--color-text-secondary);
  border:     1px solid var(--color-border-strong);

  &:hover {
    color:        var(--color-text-primary);
    border-color: var(--color-text-secondary);
  }
}

// ── Estado: cargando / skeleton ───────────────────────────────────────────

.ed-<nombre>--loading {
  @extend %ed-<nombre>-base;
  background: var(--color-surface-sunken);
  color:      transparent;

  // Skeleton shimmer
  @include motion-safe {
    animation: skeleton-pulse 1.4s ease-in-out infinite;
  }
}

// ── Subcomponentes (BEM elements) ─────────────────────────────────────────

.ed-<nombre>__header {
  @include mono-eyebrow;
  margin-bottom: $space-2;
}

.ed-<nombre>__title {
  @include display-title(1.2rem);
  margin-bottom: $space-3;
}

.ed-<nombre>__body {
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.ed-<nombre>__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $space-4;
  padding-top: $space-3;
  border-top: 1px solid var(--color-border-subtle);
  font-size: 0.75rem;
  color: var(--color-text-tertiary);
}

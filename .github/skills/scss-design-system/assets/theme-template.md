// ============================================
// Tema: <nombre-tema> — Editorial Reactiva
//
// Pasos para activar:
//   1. Agregar $theme-<nombre> en _tokens.scss
//   2. Agregar [data-theme="<nombre>"] en _base.scss
//   3. Agregar "<nombre>" al array de temas en public/javascripts/theme.js
// ============================================

// ── PASO 1: Mapa Tier 2 en _tokens.scss ──────────────────────────────────
// Copiar este bloque dentro de _tokens.scss, junto a $theme-crema / $theme-noche.

$theme-<nombre>: (
  // Superficies
  color-surface:          #,    // fondo principal — base del tema
  color-surface-raised:   #,    // cards, modales
  color-surface-sunken:   #,    // inputs, backgrounds hundidos
  color-surface-strong:   #,    // sidebars, paneles

  // Texto (verificar ratios WCAG AA: ≥ 4.5:1 sobre color-surface)
  color-text-primary:     #,    // ratio objetivo ≥ 7:1
  color-text-secondary:   #,    // ratio objetivo ≥ 4.5:1
  color-text-tertiary:    #,    // ratio objetivo ≥ 4.5:1

  // Bordes
  color-border-subtle:    rgba(0, 0, 0, 0.08),
  color-border-strong:    rgba(0, 0, 0, 0.18),

  // Acento (verificar ratio sobre color-surface: ≥ 3:1 para UI, ≥ 4.5:1 para texto)
  color-accent:           #,
  color-accent-hover:     #,    // ≈ 10-15% más oscuro/claro que color-accent
  color-accent-rgb:       "R, G, B",   // valores numéricos para rgba()
  color-accent-soft:      rgba(0, 0, 0, 0.10),  // fondo de badges/chips
  color-on-accent:        #,    // texto sobre color-accent — ratio ≥ 4.5:1

  // Foco (accesibilidad — debe destacarse sobre cualquier superficie)
  color-focus-ring:       #,

  // Estados (ajustar luminosidad si el tema es oscuro)
  color-success:          #,
  color-warning:          #,
  color-danger:           #,
  color-info:             #,
);

// ── PASO 2: Selector de tema en _base.scss ────────────────────────────────
// Agregar este bloque en _base.scss, después de [data-theme="noche"].

// [data-theme="<nombre>"] {
//   @include emit-theme($theme-<nombre>);
// }

// Si el tema es oscuro y debe activarse con prefers-color-scheme:
// [data-theme="auto"] {
//   @media (prefers-color-scheme: dark) {
//     @include emit-theme($theme-<nombre>);  // reemplaza noche como default auto
//   }
// }

// ── PASO 3: Registrar en theme.js ─────────────────────────────────────────
// En public/javascripts/theme.js, agregar al array de temas:
//
// const THEMES = ['crema', 'noche', 'auto', '<nombre>'];
//
// Si quieres que aparezca en el theme-toggle de la UI, agregar también
// la opción en shared/_theme-toggle.scss con su label y ícono.

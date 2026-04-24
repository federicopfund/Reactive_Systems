# Sistema de Design Tokens — Manifiesto Reactivo

> Issue [#16](https://github.com/federicopfund/Reactive_Systems/issues/16) · Implementado en `app/assets/stylesheets/_tokens.scss`.

Este documento es la fuente de verdad para colores, espacio, tipografía,
radii, sombras y animaciones. Cualquier valor *hard-coded* en componentes
debe migrar a un token de esta jerarquía.

---

## 1. Arquitectura de tres niveles

```
┌──────────────────────────────────────────────────────────────────┐
│  Tier 1 · PRIMITIVES                                             │
│  Variables SCSS con paletas crudas: $crema-50..900, $ink-*,      │
│  $terracota-*, $success-500, $danger-500, etc.                   │
│  No se usan directamente en componentes.                         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Tier 2 · SEMANTIC (CSS Custom Properties)                       │
│  --color-surface, --color-text-primary, --color-accent,          │
│  --color-focus-ring, --color-on-accent, --color-border-subtle…   │
│  Cambian dinámicamente según [data-theme="crema|noche|auto"].    │
│  Esta es la API que consumen los componentes.                    │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Tier 3 · COMPONENT (uso final)                                  │
│  .ed-btn  { background: var(--color-accent); }                   │
│  .ed-card { background: var(--color-surface-raised); }           │
└──────────────────────────────────────────────────────────────────┘
```

**Regla de oro:** los componentes nunca leen primitives directamente,
sólo CSS vars semánticas. Esto garantiza que cambiar de tema afecte a
todo el sistema sin tocar componentes.

---

## 2. Tokens semánticos disponibles

### Color — Superficies
| Token | Uso |
|-------|-----|
| `--color-surface` | Fondo base de la página |
| `--color-surface-raised` | Tarjetas, dialogs, modales |
| `--color-surface-sunken` | Inputs, áreas hundidas |
| `--color-surface-strong` | Hover/destacado de superficies |

### Color — Texto
| Token | Uso | Contraste mínimo |
|-------|-----|------------------|
| `--color-text-primary` | Cuerpo principal, headings | 13:1 ✓ |
| `--color-text-secondary` | Subtítulos, metadata | 7:1 ✓ |
| `--color-text-tertiary` | Captions, timestamps | 4.5:1 ✓ AA |

### Color — Bordes y acentos
| Token | Uso |
|-------|-----|
| `--color-border-subtle` | Separadores discretos |
| `--color-border-strong` | Bordes con presencia |
| `--color-accent` | Marca, CTAs, dots activos |
| `--color-accent-hover` | Hover de CTAs |
| `--color-accent-soft` | Backgrounds suaves de elementos activos |
| `--color-on-accent` | Texto sobre superficie de acento (4.6:1+ ✓) |
| `--color-focus-ring` | Anillo de foco (Issue #16-E) |

### Color — Estado
`--color-success`, `--color-warning`, `--color-danger`, `--color-info`.

### Espaciado (escala 4px)
`--space-0..9` y SCSS `$space-0..9`.

| Token | Valor | Uso típico |
|-------|-------|------------|
| `$space-0` | 0      | reset |
| `$space-1` | 0.25rem | gap mínimo |
| `$space-2` | 0.5rem  | padding interno |
| `$space-3` | 0.75rem | gap chico |
| `$space-4` | 1rem    | padding estándar |
| `$space-5` | 1.5rem  | padding cómodo |
| `$space-6` | 2rem    | sección chica |
| `$space-7` | 3rem    | sección estándar |
| `$space-8` | 4rem    | sección cómoda |
| `$space-9` | 6rem    | hero / spacing dramático |

### Tipografía fluida
Usa `clamp()` para escalar entre breakpoints sin media queries.

| Token | Rango |
|-------|-------|
| `$fs-xs` | 0.75rem fijo |
| `$fs-sm` | 0.875rem fijo |
| `$fs-base` | 1rem fijo |
| `$fs-lg` | clamp(1.125, +0.6vw, 1.375) |
| `$fs-xl` | clamp(1.25, +1.2vw, 1.75) |
| `$fs-2xl` | clamp(1.5, +1.8vw, 2.25) |
| `$fs-3xl` | clamp(2, +3vw, 3) |
| `$fs-display` | clamp(2.5, +5vw, 5) |

### Radii
`$radius-sm` (2px), `$radius-md` (4px), `$radius-lg` (8px), `$radius-pill` (999px).

### Sombras
`$shadow-sm`, `$shadow-md`, `$shadow-lg`.

### Z-index
`$z-base`, `$z-sticky`, `$z-overlay`, `$z-modal`, `$z-toast`.

### Duración / easing
`$duration-fast` (120ms), `$duration-base` (220ms), `$duration-slow` (380ms),
`$ease-out` (`cubic-bezier(.2,.7,.2,1)`).

---

## 3. Theming

```html
<html data-theme="auto">   <!-- sigue prefers-color-scheme -->
<html data-theme="crema">  <!-- claro fijo -->
<html data-theme="noche">  <!-- oscuro fijo -->
```

Persistencia: `localStorage['ed-theme']`. El bootstrap inline en
`main.scala.html` evita FOUC aplicando el atributo antes del primer
paint. El switcher (`partials/themeToggle.scala.html`) es un
**ARIA radiogroup** con tres opciones.

---

## 4. Accesibilidad WCAG 2.1 AA

* Anillo de foco visible universal vía `@include focus-ring` (mixin).
* Skip link `.ed-skip-link` en cada layout (`#ed-main`, `#ed-bo-main`).
* `aria-current="page"` reemplaza la pista visual `.active` para nav.
* `<aside aria-label>`, `<nav aria-label>` en sidebars.
* Reducción de movimiento: regla global `prefers-reduced-motion: reduce`
  + animaciones envueltas en `@include motion-safe`.
* Contraste mínimo: todas las combinaciones text/surface ≥ 4.5:1.
  Comprobaciones documentadas inline en `_tokens.scss`.

---

## 5. Migración de tokens legacy

`_variables.scss` mantiene aliases para no romper componentes existentes:

```scss
$ed-bg     → var(--color-surface)
$ed-text   → var(--color-text-primary)
$ed-accent → var(--color-accent)
// ...
```

Los componentes nuevos deben usar **directamente** las CSS vars, no los
aliases SCSS, para que el cambio de tema sea reactivo.

---

## 6. Ejemplo: crear un componente nuevo

```scss
.ed-callout {
  padding: $space-4 $space-5;
  background: var(--color-surface-raised);
  border-left: 3px solid var(--color-accent);
  border-radius: $radius-md;
  color: var(--color-text-primary);
  box-shadow: $shadow-sm;

  @include focus-ring(2px);
  @include motion-safe {
    transition: transform $duration-fast $ease-out;
    &:hover { transform: translateY(-1px); }
  }
}
```

Este componente:
- Funciona en crema y noche sin cambios.
- Respeta `prefers-reduced-motion`.
- Cumple AA (texto sobre raised ≥ 13:1, accent en focus ≥ 4.5:1).

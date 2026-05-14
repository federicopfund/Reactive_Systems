# Catálogo de Design Tokens — Editorial Reactiva

Fuente de verdad complementaria a `app/assets/stylesheets/_tokens.scss`.

---

## Tier 1 — Primitivos SCSS

Solo se usan en `_tokens.scss` para definir los mapas Tier 2. **Nunca en componentes.**

### Paleta Crema (neutral cálida — base "papel limpio")

| Variable | Valor | Uso semántico |
|----------|-------|---------------|
| `$crema-50`  | `#ffffff` | Cards / superficies elevadas |
| `$crema-100` | `#fafaf7` | Superficie principal |
| `$crema-200` | `#f1efe9` | Superficie hundida / zebra |
| `$crema-300` | `#d8d4ca` | Borde fuerte / divisor |
| `$crema-400` | `#b3ad9f` | Iconografía secundaria |
| `$crema-500` | `#8a8478` | Texto desactivado |
| `$crema-600` | `#57534b` | Texto secundario (8.4:1 ✓) |
| `$crema-700` | `#3a3631` | Texto terciario (12.1:1 ✓) |
| `$crema-800` | `#26231f` | Gray-warm-90 |
| `$crema-900` | `#181613` | Tinta cálida (≥ 17:1 ✓) |

### Paleta Ink (base tema noche)

| Variable | Valor |
|----------|-------|
| `$ink-900` | `#14110d` — surface base noche |
| `$ink-800` | `#1c1813` |
| `$ink-700` | `#26211a` |
| `$ink-600` | `#3a3329` |
| `$ink-500` | `#5a5042` |

### Paleta Terracota (acento editorial)

| Variable | Valor | Uso |
|----------|-------|-----|
| `$terracota-300` | `#f0a878` | Acento suave |
| `$terracota-400` | `#e06a2c` | Acento en tema noche (4.6:1 ✓) |
| `$terracota-500` | `#c1440e` | Acento primario día (5.4:1 ✓) |
| `$terracota-600` | `#a93a09` | Hover en día |

### Estados

| Variable | Valor | WCAG |
|----------|-------|------|
| `$success-500` | `#198038` | 4.6:1 sobre blanco ✓ |
| `$warning-500` | `#b28600` | 4.5:1 sobre blanco ✓ |
| `$danger-500`  | `#da1e28` | 4.5:1 sobre blanco ✓ |
| `$info-500`    | `#0072c3` | 4.5:1 sobre blanco ✓ |

---

## Tier 2 — Tokens Semánticos (CSS Custom Properties)

Estos son los tokens que **usan los componentes**. Cambian automáticamente según `[data-theme]`.

### Colores de superficie

| Token | Tema crema | Tema noche | Descripción |
|-------|-----------|-----------|-------------|
| `--color-surface` | `#fafaf7` | `#14110d` | Fondo principal de la página |
| `--color-surface-raised` | `#ffffff` | `#1c1813` | Cards, modales, elementos elevados |
| `--color-surface-sunken` | `#f1efe9` | `#0d0b08` | Inputs, backgrounds hundidos |
| `--color-surface-strong` | `#d8d4ca` | `#26211a` | Sidebars, paneles diferenciados |

### Colores de texto

| Token | Tema crema | Tema noche | Uso |
|-------|-----------|-----------|-----|
| `--color-text-primary` | `#181613` | `#fafaf7` | Títulos, cuerpo principal |
| `--color-text-secondary` | `#57534b` | `#c8bfae` | Labels, metadatos |
| `--color-text-tertiary` | `#3a3631` | `#8e8473` | Placeholders, texto muy secundario |

### Colores de acento

| Token | Tema crema | Tema noche |
|-------|-----------|-----------|
| `--color-accent` | `#c1440e` | `#e06a2c` |
| `--color-accent-hover` | `#a93a09` | `#f08348` |
| `--color-accent-soft` | `rgba(193,68,14,0.10)` | `rgba(224,106,44,0.16)` |
| `--color-on-accent` | `#ffffff` | `#14110d` |

### Bordes y foco

| Token | Tema crema | Tema noche |
|-------|-----------|-----------|
| `--color-border-subtle` | `rgba(24,22,19,0.08)` | `rgba(245,240,232,0.12)` |
| `--color-border-strong` | `rgba(24,22,19,0.18)` | `rgba(245,240,232,0.24)` |
| `--color-focus-ring` | `#c1440e` | `#e06a2c` |

### Espaciado (escala 8pt)

| Token | Valor | px equivalente |
|-------|-------|---------------|
| `--space-1` | `0.25rem` | 4px |
| `--space-2` | `0.5rem`  | 8px |
| `--space-3` | `0.75rem` | 12px |
| `--space-4` | `1rem`    | 16px |
| `--space-5` | `1.5rem`  | 24px |
| `--space-6` | `2rem`    | 32px |
| `--space-7` | `2.5rem`  | 40px |
| `--space-8` | `3.5rem`  | 56px |
| `--space-9` | `5rem`    | 80px |

### Tipografía

| Token | Valor |
|-------|-------|
| `--font-display` | `'Playfair Display', Georgia, serif` |
| `--font-body`    | `'Lora', Georgia, serif` |
| `--font-mono`    | `'IBM Plex Mono', 'JetBrains Mono', monospace` |

---

## Temas disponibles

| `data-theme` | Base | Descripción |
|-------------|------|-------------|
| `crema` (default) | `$theme-crema` | Off-white tibio, acento terracota |
| `noche` | `$theme-noche` | Oscuro editorial, acento terracota claro |
| `auto` | SO preference | Sigue `prefers-color-scheme` del sistema |
| `negro` | inline en `_base.scss` | Negro profundo, acento lima `#d4ff00` |
| `azul` | inline en `_base.scss` | Azul corporativo oscuro |

---

## Aliases legacy (`--ed-*`)

Para no romper componentes durante la migración. **No usar en código nuevo.**

| Alias | Apunta a |
|-------|---------|
| `--ed-bg` | `var(--color-surface)` |
| `--ed-bg2` | `var(--color-surface-sunken)` |
| `--ed-bg3` | `var(--color-surface-strong)` |
| `--ed-text` | `var(--color-text-primary)` |
| `--ed-text-dim` | `var(--color-text-secondary)` |
| `--ed-text-faint` | `var(--color-text-tertiary)` |
| `--ed-accent` | `var(--color-accent)` |
| `--ed-border` | `var(--color-border-subtle)` |
| `--ed-border-strong` | `var(--color-border-strong)` |

---

## Breakpoints

| Variable SCSS | Valor | Mixin |
|---------------|-------|-------|
| `$bp-sm` | `640px`  | `@include respond-to(sm)` |
| `$bp-md` | `900px`  | `@include respond-to(md)` |
| `$bp-lg` | `1100px` | `@include respond-to(lg)` |
| `$bp-xl` | `1400px` | `@include respond-to(xl)` |

---
name: scss-design-system
description: 'Diseño e implementación de estilos SCSS en este proyecto siguiendo la arquitectura de 3 niveles de tokens (Primitivos → Semánticos → Componente), el sistema de temas runtime (crema/noche/auto), las convenciones de nomenclatura ed-* y el orden de cascada de main.scss. Usar esta skill en los siguientes casos específicos: crear un nuevo componente SCSS, agregar una variante de color a un tema, migrar valores hard-coded a tokens semánticos, crear un nuevo tema runtime, agregar un mixin de diseño, registrar un nuevo parcial en main.scss, auditar contraste de accesibilidad WCAG en un componente.'
argument-hint: 'Nombre del componente o parcial a crear/modificar (ej: "_card.scss en shared/", "nuevo tema oscuro azul")'
---

# SCSS Design System — Skill

Guía canónica para crear, extender y auditar estilos en la **Editorial Reactiva**
(Play 3 + SbtWeb + sbt-digest + sbt-gzip), basada en la arquitectura de
**3 niveles de tokens** documentada en [docs/design-tokens.md](../../../docs/design-tokens.md).

---

## Arquitectura de tokens — 3 niveles

```
_tokens.scss
├── Tier 1 · PRIMITIVOS (solo SCSS, nunca en componentes)
│   $crema-50…900, $ink-500…900, $terracota-300/400/500/600
│   $success-500, $warning-500, $danger-500, $info-500
│
├── Tier 2 · SEMÁNTICOS (CSS Custom Properties — API de los componentes)
│   --color-surface, --color-surface-raised, --color-surface-sunken
│   --color-text-primary, --color-text-secondary, --color-text-tertiary
│   --color-accent, --color-accent-hover, --color-accent-soft
│   --color-border-subtle, --color-border-strong
│   --color-focus-ring, --color-on-accent
│   --space-1 … --space-9
│   --font-display, --font-body, --font-mono
│
└── Tier 3 · COMPONENTE (usa solo Tier 2)
    .ed-btn { background: var(--color-accent); }
    .ed-card { background: var(--color-surface-raised); }
```

**Regla de oro:** los componentes nunca leen `$primitivos`; solo `var(--semántico)`.
Referencia completa: [./references/token-tiers.md](./references/token-tiers.md)

---

## Cuándo usar esta skill

| Situación | Prompt de ejemplo |
|-----------|-------------------|
| Nuevo componente SCSS | `"Crea _notification-card.scss en shared/"` |
| Nueva variante de un tema | `"Agrega --color-highlight al tema noche"` |
| Migrar valor hard-coded | `"Migra #c1440e a var(--color-accent) en _article.scss"` |
| Nuevo tema runtime | `"Crea un tema 'sepia' para modo lectura"` |
| Nuevo mixin de diseño | `"Crea un mixin truncate-lines(n)"` |
| Registrar parcial | `"Registra _notification-card.scss en main.scss"` |
| Auditar accesibilidad | `"Verifica contraste WCAG AA de --color-text-secondary sobre --color-surface"` |

---

## Procedimiento de implementación

### 1. Determinar el nivel del cambio

| ¿Qué cambias? | Nivel | Archivo |
|---------------|-------|---------|
| Nuevo color de marca / paleta cruda | Tier 1 | `_tokens.scss` — bloque Primitivos |
| Nuevo rol semántico (`--color-*`) | Tier 2 | `_tokens.scss` — mapas `$theme-*` |
| Nueva regla de componente | Tier 3 | `shared/`, `tags/`, dominio correspondiente |
| Compatibilidad con código legacy | Alias | `_variables.scss` |

---

### 2. Crear un nuevo componente SCSS

**Ubicación según alcance:**

| Alcance | Carpeta | Ejemplo |
|---------|---------|---------|
| Átomo reutilizable en vistas | `tags/` | `_status-pill.scss` |
| Shared — diseño transversal | `shared/` | `_toast.scss` |
| Solo en sección landing | `sections/` | `_hero.scss` |
| Dominio específico | `<dominio>/` | `publications/_article.scss` |
| Solo admin/backoffice | `admin/` | `admin/_table.scss` |

**Estructura base de un componente:**

```scss
// ============================================
// <NombreComponente> — Editorial Reactiva
// ============================================

// Placeholder (base compartida entre variantes, sin output directo)
%ed-<nombre>-base {
  // propiedades estructurales (display, position, etc.)
}

// Variante principal
.ed-<nombre> {
  @extend %ed-<nombre>-base;
  background: var(--color-surface-raised);
  color:      var(--color-text-primary);
  border:     1px solid var(--color-border-subtle);
  @include focus-ring;                        // accesibilidad siempre
}

// Variante modificadora (BEM: ed-<nombre>--<mod>)
.ed-<nombre>--accent {
  @extend %ed-<nombre>-base;
  background: var(--color-accent);
  color:      var(--color-on-accent);
}

// Estado (usando & para anidamiento)
.ed-<nombre> {
  &:hover  { opacity: 0.9; }
  &:active { transform: scale(0.98); }

  // Responsive — siempre al final del bloque
  @include respond-to(md) {
    // overrides para ≤ 900px
  }
}
```

Plantilla completa: [./assets/component-template.scss](./assets/component-template.scss)

---

### 3. Agregar tokens a un tema existente

En `_tokens.scss`, localizar el mapa del tema y agregar la clave semántica:

```scss
$theme-crema: (
  // ... tokens existentes ...
  color-highlight: rgba(193, 68, 14, 0.12),   // ← nuevo token
);
```

Si el token debe existir en **todos los temas**, agregarlo en cada mapa (`$theme-crema`, `$theme-noche`) para evitar que `var(--color-highlight)` quede sin definir en algún tema.

```scss
$theme-noche: (
  // ...
  color-highlight: rgba(212, 255, 0, 0.15),   // ← mismo rol, distinto valor
);
```

El mixin `@mixin emit-theme` en `_base.scss` emite automáticamente las nuevas vars sin cambios adicionales.

---

### 4. Crear un nuevo tema runtime

En `_tokens.scss`, definir el mapa Tier 2:

```scss
$theme-sepia: (
  color-surface:          #f4efe6,
  color-surface-raised:   #faf6f0,
  color-surface-sunken:   #ede5d8,
  color-surface-strong:   #d6ccbc,
  color-text-primary:     #2c1f0e,
  color-text-secondary:   #5c4a30,
  color-text-tertiary:    #7a6048,
  color-border-subtle:    rgba(44, 31, 14, 0.10),
  color-border-strong:    rgba(44, 31, 14, 0.22),
  color-accent:           #8b4513,
  color-accent-hover:     #6b3410,
  color-accent-soft:      rgba(139, 69, 19, 0.15),
  color-on-accent:        #faf6f0,
  color-focus-ring:       #8b4513,
);
```

En `_base.scss`, registrar el selector de tema:

```scss
[data-theme="sepia"] {
  @include emit-theme($theme-sepia);
}
```

En el JS de `public/javascripts/theme.js`, agregar `"sepia"` al array de temas disponibles.

Plantilla completa: [./assets/theme-template.scss](./assets/theme-template.scss)

---

### 5. Agregar un mixin reutilizable

En `_mixins.scss`, seguir la firma de los mixins existentes:

```scss
// Truncar texto en N líneas con ellipsis
@mixin truncate-lines($n: 1) {
  display: -webkit-box;
  -webkit-line-clamp: $n;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

// Card elevada con sombra de tema
@mixin elevated-card($radius: 6px) {
  background:    var(--color-surface-raised);
  border:        1px solid var(--color-border-subtle);
  border-radius: $radius;
  box-shadow:    0 2px 8px rgba(0, 0, 0, 0.06);
  @include motion-safe {
    transition: box-shadow 0.2s, transform 0.2s;
    &:hover { box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1); }
  }
}
```

---

### 6. Registrar el parcial en main.scss

El orden de cascada es **determinístico**. Respetar la capa correcta:

```scss
// En main.scss, ubicar la @import en la capa correspondiente:

// Tags (átomos)           → bloque 4
// Shared (transversal)    → bloque 5
// Sections (landing)      → bloque 6
// Dominio público         → bloque 7
// Auth                    → bloque 8
// User                    → bloque 9
// Admin (SIEMPRE último)  → bloque 11

@import 'shared/<nombre-componente>';
```

**Regla:** Admin va último porque usa `.ed-admin-body-root` como scope y debe pisar los estilos públicos.

---

### 7. Checklist de accesibilidad WCAG AA

Antes de hacer merge, verificar cada nuevo token de color:

- [ ] Texto sobre superficie: ratio ≥ **4.5:1** (cuerpo) · **3:1** (headings ≥ 18px / bold ≥ 14px)
- [ ] `--color-text-primary` sobre `--color-surface` ≥ 4.5:1
- [ ] `--color-text-secondary` sobre `--color-surface` ≥ 4.5:1
- [ ] `--color-on-accent` sobre `--color-accent` ≥ 4.5:1
- [ ] `@include focus-ring` presente en todos los elementos interactivos
- [ ] `@include motion-safe` envuelve todas las animaciones no críticas
- [ ] Sin valores `color:` ni `background:` hard-coded en componentes (usar `var(--*)`)

Herramienta de referencia: [https://webaim.org/resources/contrastchecker/](https://webaim.org/resources/contrastchecker/)

---

## Convenciones de nomenclatura

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| Archivo parcial | `_kebab-case.scss` con guión bajo | `_status-pill.scss` |
| Clase componente | `.ed-<nombre>` | `.ed-card` |
| Modificador BEM | `.ed-<nombre>--<mod>` | `.ed-card--featured` |
| Placeholder | `%ed-<nombre>-base` | `%ed-btn-base` |
| Variable SCSS primitiva | `$nombre-escala` | `$crema-700` |
| Variable SCSS de layout | `$nombre-descriptivo` | `$sidebar-width` |
| CSS custom property | `--color-<rol>` / `--space-N` | `--color-accent` |
| Tema legacy | `--ed-<nombre>` | `--ed-accent` (alias de compat) |

---

## Pipeline de build (SbtWeb)

```
SCSS fuente                      →  sbt-sass  →  CSS compilado
app/assets/stylesheets/main.scss →             →  target/web/public/main/stylesheets/main.css
                                               →  sbt-digest  →  main-<hash>.css
                                               →  sbt-gzip    →  main-<hash>.css.gz
```

En producción, las vistas Twirl referencian el asset fingerprinted via `@routes.Assets.versioned(...)`.

---

## Recursos del skill

| Archivo | Propósito |
|---------|-----------|
| [./references/token-tiers.md](./references/token-tiers.md) | Catálogo completo de tokens Tier 1 y Tier 2 |
| [./assets/component-template.scss](./assets/component-template.scss) | Plantilla de componente con variantes y responsive |
| [./assets/theme-template.scss](./assets/theme-template.scss) | Plantilla de nuevo tema runtime |

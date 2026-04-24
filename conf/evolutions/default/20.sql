# --- Catalogo de categorias de publicaciones (taxonomia editorial)
# --- Reemplaza la lista hardcodeada en publicaciones.scala.html
# --- y permite gestion administrable.

# --- !Ups

-- ============================================================
-- Tabla: publication_categories
--
-- Catalogo de categorias usadas por publications y editorial_articles.
-- Antes estaba como lista literal en la vista, ahora vive en DB para
-- que el equipo editorial pueda agregar/desactivar sin deploy.
--
-- Invariantes:
--   - slug es URL-safe y unico
--   - order_index controla el orden en filtros y selectores
--   - active=false oculta de selectores publicos pero preserva FK
-- ============================================================

CREATE TABLE IF NOT EXISTS publication_categories (
    id          BIGSERIAL    PRIMARY KEY,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    icon_emoji  VARCHAR(10),
    order_index INT          NOT NULL DEFAULT 100,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pub_categories_active_order
    ON publication_categories(active, order_index);

-- ============================================================
-- Seed: las 5 categorias usadas hoy en la portada de publicaciones
-- ============================================================
INSERT INTO publication_categories (slug, name, description, icon_emoji, order_index)
VALUES
    ('tutorial',       'Tutorial',        'Piezas paso a paso con codigo ejecutable.',   '📘', 10),
    ('articulo',       'Artículo',        'Ensayo o analisis editorial.',                '📝', 20),
    ('guia',           'Guía',            'Material de referencia y mejores practicas.', '🧭', 30),
    ('caso-de-estudio','Caso de Estudio', 'Experiencias reales de produccion.',          '🔬', 40),
    ('recurso',        'Recurso',         'Recopilaciones, links y herramientas.',       '🧰', 50)
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- Backfill: enlazar publications.category (string libre) al catalogo.
-- Se agrega category_id como Option, sin romper datos existentes.
-- ============================================================

ALTER TABLE publications
    ADD COLUMN IF NOT EXISTS category_id BIGINT
    REFERENCES publication_categories(id) ON DELETE SET NULL;

-- Match exacto por nombre (case-insensitive). Lo no resuelto queda NULL
-- y el campo legacy `category` sigue siendo la fuente.
UPDATE publications p
SET category_id = pc.id
FROM publication_categories pc
WHERE p.category_id IS NULL
  AND LOWER(TRIM(p.category)) = LOWER(pc.name);

CREATE INDEX IF NOT EXISTS idx_publications_category_id
    ON publications(category_id);

# --- !Downs

DROP INDEX IF EXISTS idx_publications_category_id;
ALTER TABLE publications DROP COLUMN IF EXISTS category_id;
DROP INDEX IF EXISTS idx_pub_categories_active_order;
DROP TABLE IF EXISTS publication_categories;

# --- Colecciones tematicas curadas por editores.
# --- Reemplaza las 3 cards placeholder de portafolio.scala.html.

# --- !Ups

-- ============================================================
-- Tabla: collections
--
-- Una coleccion agrupa piezas (de publications y editorial_articles)
-- bajo una linea editorial: "Akka & Actores", "Resiliencia", etc.
-- El curador es un usuario (admin o editor).
-- ============================================================

CREATE TABLE IF NOT EXISTS collections (
    id           BIGSERIAL    PRIMARY KEY,
    slug         VARCHAR(150) NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    cover_label  VARCHAR(50)  NOT NULL DEFAULT 'COLECCIÓN', -- texto mostrado sobre el placeholder visual
    curator_id   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    is_published BOOLEAN      NOT NULL DEFAULT TRUE,
    order_index  INT          NOT NULL DEFAULT 100,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_collections_published_order
    ON collections(is_published, order_index);

-- ============================================================
-- Tabla: collection_items
--
-- Junction: una pieza puede aparecer en varias colecciones.
-- item_type discrimina entre "publication" (user-generated) y
-- "editorial_article" (sistema). item_id apunta al PK del tipo.
--
-- No FK polimorfica: validamos consistencia desde la app.
-- ============================================================

CREATE TABLE IF NOT EXISTS collection_items (
    id            BIGSERIAL   PRIMARY KEY,
    collection_id BIGINT      NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    item_type     VARCHAR(30) NOT NULL, -- 'publication' | 'editorial_article'
    item_id       BIGINT      NOT NULL,
    order_index   INT         NOT NULL DEFAULT 100,
    added_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_collection_item UNIQUE (collection_id, item_type, item_id),
    CONSTRAINT chk_item_type CHECK (item_type IN ('publication', 'editorial_article'))
);

CREATE INDEX IF NOT EXISTS idx_collection_items_collection
    ON collection_items(collection_id, order_index);

-- Sin seed: las colecciones se crean desde el panel admin.

# --- !Downs

DROP INDEX IF EXISTS idx_collection_items_collection;
DROP TABLE IF EXISTS collection_items;
DROP INDEX IF EXISTS idx_collections_published_order;
DROP TABLE IF EXISTS collections;

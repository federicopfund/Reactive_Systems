# --- Issue #20 — Sistema de Colecciones Editoriales: workflow, historial, curaduria
# --- Aditiva sobre la tabla collections de la evolution 23.

# --- !Ups

ALTER TABLE collections
    ADD COLUMN IF NOT EXISTS status        VARCHAR(20)  NOT NULL DEFAULT 'draft',
    ADD COLUMN IF NOT EXISTS created_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS submitted_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewed_by   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS published_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS published_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_notes  TEXT,
    ADD COLUMN IF NOT EXISTS accent_color  VARCHAR(20);

ALTER TABLE collections
    DROP CONSTRAINT IF EXISTS chk_collection_status;

ALTER TABLE collections
    ADD CONSTRAINT chk_collection_status
        CHECK (status IN ('draft','in_review','approved','rejected','published','archived'));

UPDATE collections
   SET status = 'published'
 WHERE is_published = TRUE
   AND status = 'draft';

UPDATE collections
   SET is_published = (status = 'published');

CREATE INDEX IF NOT EXISTS idx_collections_status_updated
    ON collections (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_collections_created_by
    ON collections (created_by);

ALTER TABLE collection_items
    ADD COLUMN IF NOT EXISTS curator_note TEXT;

CREATE TABLE IF NOT EXISTS collection_status_history (
    id            BIGSERIAL    PRIMARY KEY,
    collection_id BIGINT       NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20)  NOT NULL,
    actor_id      BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    actor_role    VARCHAR(50),
    comment       TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cshistory_collection_time
    ON collection_status_history (collection_id, created_at DESC);

INSERT INTO collection_status_history (collection_id, from_status, to_status, actor_id, actor_role, comment, created_at)
SELECT c.id, NULL, c.status, c.curator_id, 'system_backfill', 'Estado inicial al migrar a workflow editorial', c.created_at
  FROM collections c
 WHERE NOT EXISTS (
    SELECT 1 FROM collection_status_history h WHERE h.collection_id = c.id
 );

# --- !Downs

DROP INDEX IF EXISTS idx_cshistory_collection_time;
DROP TABLE IF EXISTS collection_status_history;

DROP INDEX IF EXISTS idx_collections_created_by;
DROP INDEX IF EXISTS idx_collections_status_updated;

ALTER TABLE collection_items
    DROP COLUMN IF EXISTS curator_note;

ALTER TABLE collections
    DROP CONSTRAINT IF EXISTS chk_collection_status;

ALTER TABLE collections
    DROP COLUMN IF EXISTS accent_color,
    DROP COLUMN IF EXISTS review_notes,
    DROP COLUMN IF EXISTS published_at,
    DROP COLUMN IF EXISTS published_by,
    DROP COLUMN IF EXISTS reviewed_at,
    DROP COLUMN IF EXISTS reviewed_by,
    DROP COLUMN IF EXISTS submitted_at,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS status;

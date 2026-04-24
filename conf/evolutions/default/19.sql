# --- Publication revisions with idempotent v1 backfill

# --- !Ups

-- ============================================================
-- Tabla: publication_revisions
--
-- Versiones numeradas del texto de una publicación. Cada vez que
-- el autor incorpora cambios tras feedback editorial y entrega,
-- se crea una nueva revisión.
--
-- El modelo guarda snapshots completos. El diff contra versiones
-- anteriores se calcula en aplicación cuando hace falta (más simple
-- de mantener que persistir deltas).
--
-- Invariantes:
--   - Cada publicación tiene al menos una revisión (v1 creada al
--     ingresar o retroactivamente para piezas anteriores a Sprint 1).
--   - version_number es monotónicamente creciente por publication_id.
--   - UNIQUE (publication_id, version_number) previene colisiones
--     en inserts concurrentes.
-- ============================================================

CREATE TABLE IF NOT EXISTS publication_revisions (
    id              BIGSERIAL PRIMARY KEY,
    publication_id  BIGINT       NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    version_number  INT          NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    excerpt         VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    change_summary  TEXT,

    CONSTRAINT uq_revision_per_publication UNIQUE (publication_id, version_number),
    CONSTRAINT chk_version_positive CHECK (version_number > 0)
);

CREATE INDEX IF NOT EXISTS idx_revisions_publication
    ON publication_revisions(publication_id, version_number DESC);

CREATE INDEX IF NOT EXISTS idx_revisions_created_by
    ON publication_revisions(created_by);

-- ============================================================
-- Backfill idempotente: crear revisión v1 para cada publicación
-- que aún no tiene revisiones.
--
-- El filtro `WHERE NOT EXISTS` garantiza que reejecutar la
-- evolution no duplique v1, y el constraint UNIQUE protege contra
-- inserciones concurrentes.
-- ============================================================

INSERT INTO publication_revisions
    (publication_id, version_number, title, content, excerpt,
     created_at, created_by, change_summary)
SELECT
    p.id,
    1,
    p.title,
    p.content,
    p.excerpt,
    p.created_at,
    p.user_id,
    'Versión inicial (migración retroactiva Sprint 1)'
FROM publications p
WHERE NOT EXISTS (
    SELECT 1 FROM publication_revisions r
    WHERE r.publication_id = p.id
);

-- ============================================================
-- FK de publication_feedback.revision_id a publication_revisions.
-- Ahora que la tabla de revisiones existe, establecemos la
-- integridad referencial que quedó pendiente en la evolution 18.
--
-- La constraint se agrega solo si aún no existe (idempotente).
-- ============================================================

ALTER TABLE publication_feedback DROP CONSTRAINT IF EXISTS fk_feedback_revision;
ALTER TABLE publication_feedback ADD CONSTRAINT fk_feedback_revision
    FOREIGN KEY (revision_id) REFERENCES publication_revisions(id)
    ON DELETE SET NULL;

# --- !Downs

ALTER TABLE publication_feedback DROP CONSTRAINT IF EXISTS fk_feedback_revision;

DROP INDEX IF EXISTS idx_revisions_created_by;
DROP INDEX IF EXISTS idx_revisions_publication;
DROP TABLE IF EXISTS publication_revisions;

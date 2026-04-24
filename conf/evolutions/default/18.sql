# --- Extend publication_feedback for editorial workflow

# --- !Ups

-- ============================================================
-- Evolución de publication_feedback existente (evolution 8).
-- Se agregan siete campos para convertirla en el sistema
-- completo de feedback editorial.
--
-- IMPORTANTE: la tabla sigue llamándose publication_feedback.
-- El campo legado admin_id se conserva con ese nombre (aunque
-- conceptualmente representa "el editor que comentó", sea admin
-- del sistema o editor del equipo editorial).
--
-- Los siete campos nuevos (todos con IF NOT EXISTS para idempotencia):
--
--   revision_id      : revisión del texto sobre la que aplica el
--                      comentario. La FK a publication_revisions se
--                      agrega en la evolution 19 (cuando esa tabla
--                      exista).
--
--   parent_id        : comentario padre para hilos de discusión.
--                      NULL = comentario raíz.
--
--   anchor_selector  : selector opaco del fragmento del texto.
--                      Su formato lo decide el editor frontend.
--                      NULL = comentario general (no anclado).
--
--   anchor_text      : texto citado al momento del comentario,
--                      preservado aunque el texto cambie.
--
--   resolved_at      : cuándo se resolvió el comentario.
--                      NULL = pendiente.
--
--   resolved_by      : quién lo resolvió (autor o editor).
--
--   visibility       : 'both' | 'editor_only'.
--                      Se deriva/sincroniza con sent_to_user:
--                        sent_to_user=true  ↔ visibility='both'
--                        sent_to_user=false ↔ visibility='editor_only'
-- ============================================================

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS revision_id BIGINT;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS parent_id BIGINT
    REFERENCES publication_feedback(id) ON DELETE CASCADE;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS anchor_selector TEXT;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS anchor_text TEXT;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS resolved_by BIGINT
    REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE publication_feedback ADD COLUMN IF NOT EXISTS visibility VARCHAR(20)
    NOT NULL DEFAULT 'both';

-- Derivar visibility desde sent_to_user para filas con sent_to_user=false.
-- Las filas con sent_to_user=true ya tienen visibility='both' por default.
-- Idempotente: solo actualiza las que aún están en 'both' con sent_to_user=false.
UPDATE publication_feedback
SET    visibility = 'editor_only'
WHERE  sent_to_user = false
  AND  visibility = 'both';

-- Constraint de valores permitidos.
-- Se dropea primero para permitir re-ejecución de la evolution sin error.
ALTER TABLE publication_feedback DROP CONSTRAINT IF EXISTS chk_feedback_visibility;
ALTER TABLE publication_feedback ADD CONSTRAINT chk_feedback_visibility
    CHECK (visibility IN ('both', 'editor_only'));

-- Índices para consultas frecuentes del flujo editorial
CREATE INDEX IF NOT EXISTS idx_feedback_revision
    ON publication_feedback(publication_id, revision_id);

CREATE INDEX IF NOT EXISTS idx_feedback_parent
    ON publication_feedback(parent_id)
    WHERE parent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_feedback_unresolved
    ON publication_feedback(publication_id)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_feedback_visibility
    ON publication_feedback(visibility);

# --- !Downs

DROP INDEX IF EXISTS idx_feedback_visibility;
DROP INDEX IF EXISTS idx_feedback_unresolved;
DROP INDEX IF EXISTS idx_feedback_parent;
DROP INDEX IF EXISTS idx_feedback_revision;

ALTER TABLE publication_feedback DROP CONSTRAINT IF EXISTS chk_feedback_visibility;

ALTER TABLE publication_feedback DROP COLUMN IF EXISTS visibility;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS resolved_by;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS resolved_at;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS anchor_text;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS anchor_selector;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS parent_id;
ALTER TABLE publication_feedback DROP COLUMN IF EXISTS revision_id;

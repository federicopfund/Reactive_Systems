# --- Extend publications: editorial columns + idempotent retroactive backfill

# --- !Ups

-- ============================================================
-- PARTE 1: COLUMNAS EDITORIALES EN publications
--
-- Se agregan tres columnas que aún nadie consume desde los
-- controllers viejos. El sistema sigue funcionando igual.
--
--   current_stage_id         : cache de la etapa actual. Fuente de
--                              verdad real: publication_stage_history.
--                              Se pobla en PARTE 3.
--
--   publication_type         : tipo de pieza. Default 'article'.
--                              Valores: article | tutorial | column | interview
--
--   requires_technical_review: flag por pieza. Default false.
--                              Los tutoriales arrancan en true.
-- ============================================================
ALTER TABLE publications ADD COLUMN IF NOT EXISTS current_stage_id BIGINT
    REFERENCES editorial_stages(id);

ALTER TABLE publications ADD COLUMN IF NOT EXISTS publication_type VARCHAR(50)
    NOT NULL DEFAULT 'article';

ALTER TABLE publications ADD COLUMN IF NOT EXISTS requires_technical_review BOOLEAN
    NOT NULL DEFAULT false;

-- Ajuste por tipo inferido de la categoría existente.
-- Captura variantes: "Tutorial", "tutorial", "Guía", "guia".
UPDATE publications
SET    publication_type = 'tutorial',
       requires_technical_review = true
WHERE  LOWER(category) IN ('tutorial', 'guía', 'guia')
  AND  publication_type = 'article';

-- ============================================================
-- PARTE 2: BACKFILL IDEMPOTENTE DE publication_stage_history
--
-- Para cada publicación que AÚN NO tiene historia en el nuevo
-- modelo, crea una fila con la etapa correspondiente a su status
-- actual, según el mapeo:
--   draft    → in_draft
--   pending  → pending_approval
--   approved → published
--   rejected → archived
--
-- El filtro `WHERE NOT EXISTS` garantiza que ejecutar la evolución
-- dos veces (por si hubo un rollback parcial) no genere duplicados.
--
-- El trigger trg_close_previous_stage (del script sql/trigger_*)
-- no se dispara aquí porque no hay filas previas abiertas para
-- ninguna publicación sin historia.
-- ============================================================
INSERT INTO publication_stage_history
    (publication_id, stage_id, entered_at, exited_at, entered_by, reason)
SELECT
    p.id                                                    AS publication_id,
    s.id                                                    AS stage_id,
    COALESCE(p.published_at, p.updated_at, p.created_at)    AS entered_at,
    NULL                                                    AS exited_at,
    p.reviewed_by                                           AS entered_by,
    'Migración retroactiva desde sistema legado (Sprint 1)' AS reason
FROM publications p
JOIN editorial_stages s ON s.code = CASE p.status
    WHEN 'draft'    THEN 'in_draft'
    WHEN 'pending'  THEN 'pending_approval'
    WHEN 'approved' THEN 'published'
    WHEN 'rejected' THEN 'archived'
    ELSE 'in_draft'
END
WHERE NOT EXISTS (
    SELECT 1 FROM publication_stage_history h
    WHERE h.publication_id = p.id
);

-- ============================================================
-- PARTE 3: POBLAR current_stage_id EN CADA PUBLICACIÓN
--
-- Lo deriva de la fila abierta (exited_at IS NULL) en la historia.
-- Este UPDATE es idempotente: ejecutarlo varias veces produce el
-- mismo resultado porque el índice parcial único garantiza que
-- hay exactamente una fila abierta por publicación.
-- ============================================================
UPDATE publications p
SET    current_stage_id = h.stage_id
FROM   publication_stage_history h
WHERE  h.publication_id = p.id
  AND  h.exited_at IS NULL
  AND  (p.current_stage_id IS DISTINCT FROM h.stage_id);

-- ============================================================
-- PARTE 4: ÍNDICES PARA CONSULTAS FRECUENTES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_publications_current_stage
    ON publications(current_stage_id);

CREATE INDEX IF NOT EXISTS idx_publications_type
    ON publications(publication_type);

-- Índice parcial: solo piezas que requieren revisión técnica.
-- Es un subset pequeño, consulta frecuente en bandeja del revisor
-- (Sprint 3+).
CREATE INDEX IF NOT EXISTS idx_publications_needs_tech_review
    ON publications(requires_technical_review)
    WHERE requires_technical_review = true;

# --- !Downs

DROP INDEX IF EXISTS idx_publications_needs_tech_review;
DROP INDEX IF EXISTS idx_publications_type;
DROP INDEX IF EXISTS idx_publications_current_stage;

ALTER TABLE publications DROP COLUMN IF EXISTS requires_technical_review;
ALTER TABLE publications DROP COLUMN IF EXISTS publication_type;
ALTER TABLE publications DROP COLUMN IF EXISTS current_stage_id;

-- Nota: el Down NO borra las filas del backfill en publication_stage_history.
-- Se decidió preservar historia aunque se revierta esta evolution para no
-- perder auditoría accidentalmente. Si hay que limpiar también esas filas,
-- revertir también la evolution 16 (que dropea la tabla por CASCADE).

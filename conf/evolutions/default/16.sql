# --- Publication stage history

# --- !Ups

-- ============================================================
-- Tabla: publication_stage_history
-- Fuente de verdad del estado editorial de cada pieza.
-- Cada transición es una fila. La etapa actual es la única
-- fila con exited_at IS NULL.
-- ============================================================
CREATE TABLE publication_stage_history (
    id              BIGSERIAL PRIMARY KEY,
    publication_id  BIGINT      NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    stage_id        BIGINT      NOT NULL REFERENCES editorial_stages(id),
    entered_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exited_at       TIMESTAMP,
    entered_by      BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    reason          TEXT,
    internal_notes  TEXT
);

CREATE INDEX idx_stage_history_publication
    ON publication_stage_history(publication_id, entered_at DESC);

-- Índice parcial crítico: etapa actual por publicación en O(1)
CREATE UNIQUE INDEX idx_stage_history_current
    ON publication_stage_history(publication_id)
    WHERE exited_at IS NULL;

CREATE INDEX idx_stage_history_stage
    ON publication_stage_history(stage_id)
    WHERE exited_at IS NULL;

CREATE INDEX idx_stage_history_entered_by
    ON publication_stage_history(entered_by);

-- NOTA: El trigger close_previous_stage() se crea fuera de las
-- evoluciones de Play (via sql/triggers.sql) porque Play no
-- soporta PL/pgSQL con dollar-quoting ni string literals multilínea.

# --- !Downs

DROP TRIGGER IF EXISTS trg_close_previous_stage ON publication_stage_history;
DROP FUNCTION IF EXISTS close_previous_stage();
DROP INDEX IF EXISTS idx_stage_history_entered_by;
DROP INDEX IF EXISTS idx_stage_history_stage;
DROP INDEX IF EXISTS idx_stage_history_current;
DROP INDEX IF EXISTS idx_stage_history_publication;
DROP TABLE IF EXISTS publication_stage_history;

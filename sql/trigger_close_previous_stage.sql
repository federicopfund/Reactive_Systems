-- ============================================================
-- Trigger: close_previous_stage
-- Al insertar una nueva fila con exited_at IS NULL,
-- cierra automáticamente la fila anterior abierta de la
-- misma publicación. Garantiza la invariante de etapa única.
--
-- Se mantiene fuera de las evoluciones de Play porque el
-- parser no soporta PL/pgSQL (dollar-quoting / string literals).
--
-- Ejecutar DESPUÉS de aplicar la evolución 16:
--   docker exec -i reactive_manifesto_db psql -U reactive_user \
--     -d reactive_manifesto -f /tmp/trigger_close_previous_stage.sql
-- ============================================================

CREATE OR REPLACE FUNCTION close_previous_stage()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.exited_at IS NULL THEN
        UPDATE publication_stage_history
        SET    exited_at = NEW.entered_at
        WHERE  publication_id = NEW.publication_id
          AND  id != NEW.id
          AND  exited_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- IMPORTANTE: debe ser BEFORE INSERT (no AFTER) para que el UPDATE
-- cierre la fila anterior ANTES de que se valide el índice único
-- parcial idx_stage_history_current (WHERE exited_at IS NULL).
CREATE TRIGGER trg_close_previous_stage
    BEFORE INSERT ON publication_stage_history
    FOR EACH ROW
    EXECUTE FUNCTION close_previous_stage();

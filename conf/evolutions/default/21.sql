# --- Pilares del Manifiesto Reactivo (tesis fundacional de la edicion)
# --- Reemplaza el bloque hardcoded de 4 pilares en index.scala.html.

# --- !Ups

-- ============================================================
-- Tabla: manifesto_pillars
--
-- Los 4 pilares son el "credo" editorial. Aunque cambian raramente,
-- tenerlos en DB permite ajustar copy editorial sin deploy y abre
-- la puerta a re-numeracion / extension futura (p.ej. mas dimensiones).
--
-- Tags se guardan como TEXT separado por '|' para mantener portabilidad
-- entre H2 (dev) y PostgreSQL (prod), evitando JSONB.
-- ============================================================

CREATE TABLE IF NOT EXISTS manifesto_pillars (
    id            BIGSERIAL    PRIMARY KEY,
    pillar_number INT          NOT NULL UNIQUE,   -- 1..N
    roman_numeral VARCHAR(8)   NOT NULL,          -- I, II, III, IV
    name          VARCHAR(100) NOT NULL,
    description   TEXT         NOT NULL,
    tags_pipe     TEXT         NOT NULL DEFAULT '', -- "tag1|tag2|tag3"
    accent_color  VARCHAR(20),                    -- referencia al design system
    order_index   INT          NOT NULL DEFAULT 100,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pillars_active_order
    ON manifesto_pillars(active, order_index);

-- ============================================================
-- Seed: los 4 pilares oficiales del Reactive Manifesto (2014).
-- Copy textualmente migrado desde index.scala.html.
-- ============================================================
INSERT INTO manifesto_pillars
    (pillar_number, roman_numeral, name, description, tags_pipe, accent_color, order_index)
VALUES
    (1, 'I',   'Responsivo',
     'Los sistemas responden de manera oportuna y consistente, detectando problemas y manejando errores con efectividad.',
     'Tiempo de respuesta|Detección de fallos|Manejo de errores',
     'responsive', 10),

    (2, 'II',  'Resiliente',
     'El sistema permanece responsivo ante fallos mediante replicación, contención, aislamiento y delegación.',
     'Replicación|Aislamiento|Supervisión',
     'resilient', 20),

    (3, 'III', 'Elástico',
     'El sistema se adapta ante cargas variables, sin cuellos de botella, distribuyendo dinámicamente.',
     'Escalado dinámico|Sin cuellos de botella|Distribución de carga',
     'elastic', 30),

    (4, 'IV',  'Orientado a Mensajes',
     'El paso asíncrono de mensajes establece límites entre componentes, desacopla y permite backpressure.',
     'Comunicación asíncrona|Desacoplamiento|Backpressure',
     'message', 40)
ON CONFLICT (pillar_number) DO NOTHING;

# --- !Downs

DROP INDEX IF EXISTS idx_pillars_active_order;
DROP TABLE IF EXISTS manifesto_pillars;

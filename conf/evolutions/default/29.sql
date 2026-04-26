# --- Issue #22 — Temporadas editoriales + asociación opcional en publicaciones

# --- !Ups

CREATE TABLE IF NOT EXISTS editorial_seasons (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    starts_on   DATE,
    ends_on     DATE,
    is_current  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Solo puede existir una temporada con is_current = true
CREATE UNIQUE INDEX IF NOT EXISTS ux_editorial_seasons_single_current
    ON editorial_seasons(is_current)
    WHERE is_current = true;

ALTER TABLE publications
    ADD COLUMN IF NOT EXISTS season_id BIGINT
    REFERENCES editorial_seasons(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_publications_season_id
    ON publications(season_id);

-- Seed para desarrollo: temporada actual por defecto
INSERT INTO editorial_seasons (code, name, description, starts_on, ends_on, is_current)
VALUES (
    '2026-q2',
    'Temporada 2026 Q2',
    'Temporada editorial inicial para desarrollo.',
    DATE '2026-04-01',
    DATE '2026-06-30',
    TRUE
) ON CONFLICT (code) DO NOTHING;

# --- !Downs

DROP INDEX IF EXISTS idx_publications_season_id;
ALTER TABLE publications DROP COLUMN IF EXISTS season_id;

DROP INDEX IF EXISTS ux_editorial_seasons_single_current;
DROP TABLE IF EXISTS editorial_seasons;

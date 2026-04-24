# --- Editorial stages catalog

# --- !Ups

-- ============================================================
-- Tabla: editorial_stages
-- Catálogo configurable de etapas del proceso editorial.
-- ============================================================
CREATE TABLE editorial_stages (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    label               VARCHAR(100) NOT NULL,
    description         TEXT,
    order_index         INT          NOT NULL,
    is_terminal         BOOLEAN      NOT NULL DEFAULT false,
    required_role       VARCHAR(50),
    allows_author_edit  BOOLEAN      NOT NULL DEFAULT false,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_editorial_stages_code   ON editorial_stages(code);
CREATE INDEX idx_editorial_stages_order  ON editorial_stages(order_index);
CREATE INDEX idx_editorial_stages_active ON editorial_stages(active) WHERE active = true;

-- ============================================================
-- Seed: las nueve etapas canónicas del flujo editorial.
-- Con el pitch obligatorio (decisión 8.1), toda pieza arranca
-- en 'pitch' y no hay atajos hacia adelante para esa etapa.
-- ============================================================
INSERT INTO editorial_stages
    (code, label, description, order_index, is_terminal, required_role, allows_author_edit)
VALUES
    ('pitch',                'Propuesta',
     'El autor propone un tema con sinopsis breve para evaluación editorial.',
     10, false, 'acquisitions_editor', false),

    ('accepted',             'Aceptada',
     'El editor de adquisiciones aceptó la propuesta. El autor tiene luz verde para escribir.',
     20, false, 'author', false),

    ('in_draft',             'En redacción',
     'El autor está escribiendo la pieza. Puede guardar sin enviar.',
     30, false, 'author', true),

    ('in_copy_edit',         'En edición',
     'El editor de estilo trabaja el texto. Deja feedback anclado. Puede devolver al autor.',
     40, false, 'copy_editor', false),

    ('in_technical_review',  'En revisión técnica',
     'Un revisor valida exactitud técnica. Solo aplica si la pieza lo requiere.',
     50, false, 'technical_reviewer', false),

    ('pending_approval',     'Pendiente de aprobación',
     'La pieza está lista y el director editorial decide si se publica.',
     60, false, 'editorial_director', false),

    ('scheduled',            'Programada',
     'Aprobada. Asignada a colección, número y fecha de publicación.',
     70, false, 'curator', false),

    ('published',            'Publicada',
     'Visible públicamente. Forma parte del catálogo editorial.',
     80, true, NULL, false),

    ('archived',             'Archivada',
     'Retirada del proceso. Puede revivirse al estado in_draft.',
     90, true, NULL, false);

# --- !Downs

DROP INDEX IF EXISTS idx_editorial_stages_active;
DROP INDEX IF EXISTS idx_editorial_stages_order;
DROP INDEX IF EXISTS idx_editorial_stages_code;
DROP TABLE IF EXISTS editorial_stages;
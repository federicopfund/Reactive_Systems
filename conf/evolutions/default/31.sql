# --- Issue #24 — Modelo de negocio: sponsors, newsletter premium, métricas editoriales
# ---
# --- Tres tablas nuevas + extensiones a tablas existentes:
# ---
# ---  1. sponsors              — empresas / organizaciones patrocinadoras
# ---  2. sponsor_agreements    — contratos de patrocinio (tier, unidad, período, precio)
# ---  3. sponsor_deliverables  — beneficios pactados y su estado de entrega
# ---  4. newsletter_subscribers (ext) — campo tier para membresías de pago
# ---  5. editorial_seasons (ext)     — campos de sponsoreo por temporada
# ---  6. collections (ext)           — campos de sponsoreo por colección
# ---  7. community_events (ext)      — campos de sponsoreo por evento
# ---
# --- Invariantes de diseño:
# ---  - Una unidad (temporada/colección/evento) solo puede tener UN acuerdo
# ---    activo por tier Gold y Silver. Bronze admite hasta 2 (enforcement en app).
# ---  - sponsor_agreements usa un status workflow:
# ---      draft → negotiating → signed → active → completed | cancelled
# ---  - sponsor_deliverables trackea cada beneficio pactado:
# ---      pending → delivered | waived
# ---  - newsletter_subscribers.tier: 'free' | 'member' | 'team'
# ---    Sin FK externa: Stripe gestiona el cobro, la app sincroniza el tier.

# --- !Ups

-- ============================================================
-- 1. SPONSORS — catálogo de empresas patrocinadoras
-- ============================================================

CREATE TABLE IF NOT EXISTS sponsors (
    id            BIGSERIAL     PRIMARY KEY,
    slug          VARCHAR(100)  NOT NULL UNIQUE,
    name          VARCHAR(200)  NOT NULL,
    website       VARCHAR(500),
    logo_url      VARCHAR(500),
    contact_name  VARCHAR(200),
    contact_email VARCHAR(320),
    contact_role  VARCHAR(100),
    tier_default  VARCHAR(20)   NOT NULL DEFAULT 'bronze',
    notes         TEXT,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sponsor_tier_default
        CHECK (tier_default IN ('gold', 'silver', 'bronze'))
);

CREATE INDEX IF NOT EXISTS idx_sponsors_active
    ON sponsors(is_active, name);

-- Seed: candidatos identificados en el modelo de negocio
INSERT INTO sponsors (slug, name, website, contact_email, tier_default, notes) VALUES
('akka',       'Akka (ex-Lightbend)', 'https://akka.io',       'programs@akka.io',           'gold',   'Stack nativo de la plataforma. Contacto de eventos y sponsoreo: programs@akka.io'),
('jetbrains',  'JetBrains',           'https://jetbrains.com', NULL,                          'gold',   'Developer Advocacy team. IDE Scala (IntelliJ + Scala plugin).'),
('aws',        'Amazon Web Services', 'https://aws.amazon.com',NULL,                          'silver', 'Colecciones sobre cloud nativa y sistemas distribuidos.'),
('scalac',     'Scalac',              'https://scalac.io',     NULL,                          'silver', 'Consultora Scala/Akka. Partner oficial de Akka desde oct 2024.'),
('confluent',  'Confluent',           'https://confluent.io',  NULL,                          'gold',   'Kafka + streams reactivos. Audiencia alineada.'),
('datadog',    'Datadog',             'https://datadoghq.com', NULL,                          'silver', 'Observabilidad. Colecciones sobre resiliencia y circuit breakers.'),
('47degrees',  '47 Degrees',          'https://47deg.com',     NULL,                          'bronze', 'Consultora Scala global. Eventos y workshops.'),
('softwaremill','SoftwareMill',       'https://softwaremill.com',NULL,                        'bronze', 'Dev shop Scala/Akka. Eventos técnicos.'),
('virtuslab',  'Virtus Lab',          'https://virtuslab.com', NULL,                          'bronze', 'Mantenedores de Scala 3. Patrocinio de eventos.')
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- 2. SPONSOR_AGREEMENTS — contratos de patrocinio
-- ============================================================

CREATE TABLE IF NOT EXISTS sponsor_agreements (
    id              BIGSERIAL    PRIMARY KEY,

    sponsor_id      BIGINT       NOT NULL REFERENCES sponsors(id) ON DELETE RESTRICT,

    -- Tier y unidad editorial patrocinada
    tier            VARCHAR(20)  NOT NULL,
    unit_type       VARCHAR(30)  NOT NULL,
    -- unit_type: 'season' | 'collection' | 'event' | 'newsletter'

    -- FK polimórfica a la unidad (solo una activa por vez)
    season_id       BIGINT       REFERENCES editorial_seasons(id) ON DELETE SET NULL,
    collection_id   BIGINT       REFERENCES collections(id)       ON DELETE SET NULL,
    event_id        BIGINT       REFERENCES community_events(id)  ON DELETE SET NULL,

    -- Precio y moneda
    price_usd       NUMERIC(10,2),
    currency        VARCHAR(10)  NOT NULL DEFAULT 'USD',

    -- Workflow
    status          VARCHAR(20)  NOT NULL DEFAULT 'draft',
    -- draft → negotiating → signed → active → completed | cancelled

    -- Fechas
    period_start    DATE,
    period_end      DATE,
    signed_at       TIMESTAMP,
    activated_at    TIMESTAMP,
    completed_at    TIMESTAMP,
    cancelled_at    TIMESTAMP,
    cancel_reason   TEXT,

    -- Trazabilidad interna
    created_by      BIGINT       REFERENCES admins(id) ON DELETE SET NULL,
    notes           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_agreement_tier
        CHECK (tier IN ('gold', 'silver', 'bronze')),

    CONSTRAINT chk_agreement_unit_type
        CHECK (unit_type IN ('season', 'collection', 'event', 'newsletter')),

    CONSTRAINT chk_agreement_status
        CHECK (status IN ('draft', 'negotiating', 'signed', 'active', 'completed', 'cancelled')),

    -- Exactamente una FK de unidad definida según unit_type
    CONSTRAINT chk_unit_coherence CHECK (
        (unit_type = 'season'     AND season_id     IS NOT NULL AND collection_id IS NULL AND event_id IS NULL) OR
        (unit_type = 'collection' AND collection_id IS NOT NULL AND season_id     IS NULL AND event_id IS NULL) OR
        (unit_type = 'event'      AND event_id      IS NOT NULL AND season_id     IS NULL AND collection_id IS NULL) OR
        (unit_type = 'newsletter' AND season_id IS NULL AND collection_id IS NULL AND event_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_agreements_sponsor
    ON sponsor_agreements(sponsor_id, status);

CREATE INDEX IF NOT EXISTS idx_agreements_season
    ON sponsor_agreements(season_id) WHERE season_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agreements_collection
    ON sponsor_agreements(collection_id) WHERE collection_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agreements_event
    ON sponsor_agreements(event_id) WHERE event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agreements_status_period
    ON sponsor_agreements(status, period_start, period_end);

-- ============================================================
-- 3. SPONSOR_DELIVERABLES — beneficios pactados y estado
-- ============================================================

CREATE TABLE IF NOT EXISTS sponsor_deliverables (
    id              BIGSERIAL    PRIMARY KEY,
    agreement_id    BIGINT       NOT NULL REFERENCES sponsor_agreements(id) ON DELETE CASCADE,

    deliverable_type VARCHAR(50) NOT NULL,
    -- Tipos: 'newsletter_mention' | 'season_cover' | 'article_footer'
    --        | 'event_logo' | 'event_slot' | 'case_study' | 'metrics_report'

    description     TEXT,
    due_date        DATE,

    -- Workflow simple
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    -- pending → delivered | waived

    delivered_at    TIMESTAMP,
    delivered_by    BIGINT       REFERENCES admins(id) ON DELETE SET NULL,
    delivery_notes  TEXT,

    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_deliverable_type CHECK (
        deliverable_type IN (
            'newsletter_mention', 'season_cover', 'article_footer',
            'event_logo', 'event_slot', 'case_study', 'metrics_report',
            'collection_cover', 'opening_essay_mention'
        )
    ),

    CONSTRAINT chk_deliverable_status
        CHECK (status IN ('pending', 'delivered', 'waived'))
);

CREATE INDEX IF NOT EXISTS idx_deliverables_agreement
    ON sponsor_deliverables(agreement_id, status);

CREATE INDEX IF NOT EXISTS idx_deliverables_due
    ON sponsor_deliverables(due_date) WHERE status = 'pending';

-- ============================================================
-- 4. NEWSLETTER_SUBSCRIBERS — agregar tier de membresía
-- ============================================================

ALTER TABLE newsletter_subscribers
    ADD COLUMN IF NOT EXISTS tier            VARCHAR(20)  NOT NULL DEFAULT 'free',
    ADD COLUMN IF NOT EXISTS tier_since      TIMESTAMP,
    ADD COLUMN IF NOT EXISTS tier_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(100);

ALTER TABLE newsletter_subscribers
    DROP CONSTRAINT IF EXISTS chk_newsletter_tier;

ALTER TABLE newsletter_subscribers
    ADD CONSTRAINT chk_newsletter_tier
        CHECK (tier IN ('free', 'member', 'team'));

CREATE INDEX IF NOT EXISTS idx_newsletter_tier
    ON newsletter_subscribers(tier) WHERE is_active = TRUE;

-- Backfill: todos los existentes son tier 'free'
UPDATE newsletter_subscribers
    SET tier = 'free'
    WHERE tier IS NULL OR tier = '';

-- ============================================================
-- 5. EDITORIAL_SEASONS — campos de sponsoreo
-- ============================================================

ALTER TABLE editorial_seasons
    ADD COLUMN IF NOT EXISTS sponsor_id          BIGINT  REFERENCES sponsors(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS sponsor_label       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sponsor_show_public BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_seasons_sponsor
    ON editorial_seasons(sponsor_id) WHERE sponsor_id IS NOT NULL;

-- ============================================================
-- 6. COLLECTIONS — campos de sponsoreo
-- ============================================================

ALTER TABLE collections
    ADD COLUMN IF NOT EXISTS sponsor_id          BIGINT  REFERENCES sponsors(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS sponsor_label       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sponsor_show_public BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_collections_sponsor
    ON collections(sponsor_id) WHERE sponsor_id IS NOT NULL;

-- ============================================================
-- 7. COMMUNITY_EVENTS — campos de sponsoreo
-- ============================================================

ALTER TABLE community_events
    ADD COLUMN IF NOT EXISTS sponsor_id          BIGINT  REFERENCES sponsors(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS sponsor_label       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sponsor_show_public BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_events_sponsor
    ON community_events(sponsor_id) WHERE sponsor_id IS NOT NULL;

# --- !Downs

ALTER TABLE community_events
    DROP COLUMN IF EXISTS sponsor_show_public,
    DROP COLUMN IF EXISTS sponsor_label,
    DROP COLUMN IF EXISTS sponsor_id;

ALTER TABLE collections
    DROP COLUMN IF EXISTS sponsor_show_public,
    DROP COLUMN IF EXISTS sponsor_label,
    DROP COLUMN IF EXISTS sponsor_id;

ALTER TABLE editorial_seasons
    DROP COLUMN IF EXISTS sponsor_show_public,
    DROP COLUMN IF EXISTS sponsor_label,
    DROP COLUMN IF EXISTS sponsor_id;

ALTER TABLE newsletter_subscribers
    DROP CONSTRAINT IF EXISTS chk_newsletter_tier;
ALTER TABLE newsletter_subscribers
    DROP COLUMN IF EXISTS stripe_customer_id,
    DROP COLUMN IF EXISTS tier_expires_at,
    DROP COLUMN IF EXISTS tier_since,
    DROP COLUMN IF EXISTS tier;

DROP INDEX IF EXISTS idx_deliverables_due;
DROP INDEX IF EXISTS idx_deliverables_agreement;
DROP TABLE IF EXISTS sponsor_deliverables;

DROP INDEX IF EXISTS idx_agreements_status_period;
DROP INDEX IF EXISTS idx_agreements_event;
DROP INDEX IF EXISTS idx_agreements_collection;
DROP INDEX IF EXISTS idx_agreements_season;
DROP INDEX IF EXISTS idx_agreements_sponsor;
DROP TABLE IF EXISTS sponsor_agreements;

DROP INDEX IF EXISTS idx_sponsors_active;
DROP TABLE IF EXISTS sponsors;
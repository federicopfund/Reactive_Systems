# --- Issue #19 — Calendario Editorial: Modulo de Eventos de la Comunidad
# --- Tablas: community_events + event_attendees
# --- !Downs
DROP TABLE IF EXISTS event_attendees;
DROP TABLE IF EXISTS community_events;

# --- !Ups

CREATE TABLE IF NOT EXISTS community_events (
    id                  BIGSERIAL    PRIMARY KEY,
    slug                VARCHAR(160) NOT NULL UNIQUE,
    title               VARCHAR(200) NOT NULL,
    summary             VARCHAR(500),
    description_html    TEXT         NOT NULL,

    event_type          VARCHAR(30)  NOT NULL DEFAULT 'talk',
    modality            VARCHAR(20)  NOT NULL DEFAULT 'online',

    starts_at           TIMESTAMP    NOT NULL,
    ends_at             TIMESTAMP    NOT NULL,
    timezone            VARCHAR(50)  NOT NULL DEFAULT 'America/Argentina/Cordoba',

    location_name       VARCHAR(200),
    location_url        VARCHAR(500),
    location_detail     TEXT,

    cover_image         VARCHAR(500),
    accent_color        VARCHAR(20),

    capacity            INT,
    tags_pipe           TEXT         NOT NULL DEFAULT '',
    speakers_json       TEXT         NOT NULL DEFAULT '[]',

    status              VARCHAR(20)  NOT NULL DEFAULT 'draft',
    cancellation_reason TEXT,

    created_by          BIGINT       NOT NULL REFERENCES users(id),
    published_by        BIGINT       REFERENCES users(id),
    published_at        TIMESTAMP,

    view_count          INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_event_status   CHECK (status IN ('draft','published','cancelled','archived')),
    CONSTRAINT chk_event_type     CHECK (event_type IN ('talk','workshop','meetup','ama','release','stream','roundtable')),
    CONSTRAINT chk_event_modality CHECK (modality IN ('online','presencial','hibrido')),
    CONSTRAINT chk_event_dates    CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_events_status_starts ON community_events(status, starts_at);
CREATE INDEX IF NOT EXISTS idx_events_published     ON community_events(status, published_at DESC) WHERE status = 'published';
CREATE INDEX IF NOT EXISTS idx_events_slug          ON community_events(slug);

CREATE TABLE IF NOT EXISTS event_attendees (
    id             BIGSERIAL   PRIMARY KEY,
    event_id       BIGINT      NOT NULL REFERENCES community_events(id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rsvp_status    VARCHAR(20) NOT NULL,
    reminder_optin BOOLEAN     NOT NULL DEFAULT TRUE,
    notes          TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_id, user_id),
    CONSTRAINT chk_rsvp CHECK (rsvp_status IN ('attending','maybe','declined'))
);

CREATE INDEX IF NOT EXISTS idx_attendees_event ON event_attendees(event_id, rsvp_status);
CREATE INDEX IF NOT EXISTS idx_attendees_user  ON event_attendees(user_id, created_at DESC);

-- ============================================================
-- Seed: 3 eventos de muestra para que la agenda no nazca vacia.
-- created_by apunta al super_admin id=1 (federico).
-- ============================================================

INSERT INTO community_events
  (slug, title, summary, description_html, event_type, modality,
   starts_at, ends_at, timezone,
   location_name, location_url,
   accent_color, tags_pipe, speakers_json, status,
   created_by, published_by, published_at)
VALUES
  ('akka-persistence-en-produccion',
   'Akka Persistence en produccion',
   'Como llegamos a 40k eventos por segundo sin perder una sola proyeccion. Con Q&A abierto.',
   '<p>Una sesion tecnica donde recorremos un caso real de Akka Persistence corriendo en produccion: estrategias de event-sourcing, tuning de Cassandra como journal, snapshots, y proyecciones idempotentes. Cerramos con un Q&amp;;A abierto a la comunidad.</p>',
   'talk', 'online',
   CURRENT_TIMESTAMP + INTERVAL '7 days',
   CURRENT_TIMESTAMP + INTERVAL '7 days' + INTERVAL '90 minutes',
   'America/Argentina/Cordoba',
   'Zoom', 'https://zoom.us/j/manifiesto',
   'resilient', 'akka|persistence|produccion|event-sourcing',
   '[{"name":"Mariana Liebana","role":"Staff Engineer","avatar":"","bio":"Trabaja en sistemas distribuidos desde hace 10 anios."}]',
   'published', 1, 1, CURRENT_TIMESTAMP),

  ('reactive-streams-deep-dive',
   'Reactive Streams: deep dive practico',
   'Workshop hands-on sobre back-pressure, Akka Streams y composicion de grafos reactivos.',
   '<p>Un workshop de 2 horas donde escribimos juntos un pipeline reactivo desde cero: ingest de un Kafka topic, transformaciones con back-pressure, sink con confirmacion. Incluye repositorio con setup listo.</p>',
   'workshop', 'hibrido',
   CURRENT_TIMESTAMP + INTERVAL '21 days',
   CURRENT_TIMESTAMP + INTERVAL '21 days' + INTERVAL '120 minutes',
   'America/Argentina/Cordoba',
   'Centro Cultural Cordoba', 'https://goo.gl/maps/manifiesto',
   'elastic', 'streams|workshop|akka|hands-on',
   '[{"name":"Tomas Pereyra","role":"Tech Lead","avatar":"","bio":""},{"name":"Sofia Albornoz","role":"Backend Engineer","avatar":"","bio":""}]',
   'published', 1, 1, CURRENT_TIMESTAMP),

  ('ama-arquitectura-reactiva',
   'AMA: Arquitectura reactiva en equipos pequenos',
   'Mesa abierta sobre como adoptar el manifiesto reactivo cuando el equipo tiene 4 personas y un deadline.',
   '<p>Una conversacion abierta donde traen sus dudas reales y las resolvemos juntos. Sin slides, sin guion, con un panel de tres ingenieras que han llevado equipos de 3 a 30 personas.</p>',
   'ama', 'online',
   CURRENT_TIMESTAMP + INTERVAL '35 days',
   CURRENT_TIMESTAMP + INTERVAL '35 days' + INTERVAL '60 minutes',
   'America/Argentina/Cordoba',
   'Discord', 'https://discord.gg/manifiesto',
   'message', 'ama|arquitectura|equipos',
   '[]',
   'published', 1, 1, CURRENT_TIMESTAMP);



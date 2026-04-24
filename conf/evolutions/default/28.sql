# --- Issue #21 — Identidad editorial: misión, tesis, audiencia.
# --- Tabla pequeña editable desde backoffice (patrón de manifesto_pillars / legal_documents).

# --- !Ups

CREATE TABLE IF NOT EXISTS editorial_identity (
    id            BIGSERIAL    PRIMARY KEY,
    section_key   VARCHAR(50)  NOT NULL UNIQUE,
    -- 'mission' | 'thesis' | 'promise' | 'audience_primary' | 'audience_secondary' | 'we_are_not'
    title         VARCHAR(200) NOT NULL,
    body_html     TEXT         NOT NULL,
    order_index   INT          NOT NULL DEFAULT 100,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_editorial_identity_active_order
    ON editorial_identity(active, order_index);

-- ============================================================
-- Seed: 6 bloques editoriales redactados.
-- Voz: sobria, técnica, sin marketing-speak. Coherente con el resto del sitio.
-- ============================================================

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'mission',
    'Misión',
    '<p>Manifiesto Reactivo es una edición digital que documenta la práctica de los sistemas reactivos: las decisiones de diseño, los patrones que sobreviven en producción y los postmortems de los que no. Existimos para que los equipos que construyen software responsivo, resiliente y elástico no tengan que aprender en el vacío.</p>',
    10
) ON CONFLICT (section_key) DO NOTHING;

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'thesis',
    'Tesis editorial',
    '<p>El Manifiesto Reactivo original, publicado en 2014, declara cuatro principios: responsivo, resiliente, elástico y orientado a mensajes. Es un documento de tesis: define el qué. Once años después, la conversación pública sigue ocupando ese mismo nivel — repetir los principios, citar las definiciones, mostrar el diagrama del rombo.</p>'
    || '<p>Nosotros documentamos el cómo. Cada pieza parte de una decisión real que un equipo tomó, en un contexto concreto, con un presupuesto de tiempo y de complejidad acotado. Mostramos qué patrón usaron, por qué lo eligieron sobre las alternativas, qué falló cuando llegó la carga, cómo lo midieron y qué aprendieron en el postmortem. Cuando el patrón funcionó, explicamos por qué. Cuando falló, explicamos por qué con la misma honestidad.</p>'
    || '<p>No publicamos opinión sin caso. No publicamos tutoriales que terminan en el "hello world". No publicamos benchmarks sin contexto. La pieza mínima de Manifiesto Reactivo combina una decisión, una medición y una conclusión replicable.</p>',
    20
) ON CONFLICT (section_key) DO NOTHING;

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'promise',
    'Promesa al lector',
    '<ul>'
    || '<li>Cada pieza está atada a un caso real, no a un ejemplo de marketing.</li>'
    || '<li>Cuando publicamos un patrón, publicamos también los modos en que falla.</li>'
    || '<li>Las decisiones de diseño se justifican contra las alternativas que se descartaron, no en abstracto.</li>'
    || '<li>Los números (latencias, throughput, costos) vienen con el contexto de medición que los hace replicables.</li>'
    || '<li>Si una pieza envejece mal, lo decimos en una nota visible, no la borramos.</li>'
    || '</ul>',
    30
) ON CONFLICT (section_key) DO NOTHING;

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'audience_primary',
    'Audiencia primaria',
    '<p>Arquitectos y desarrolladores senior que toman decisiones de diseño sobre sistemas distribuidos: cuándo introducir un actor, cuándo basta con un servicio sin estado, cómo dimensionar un circuit breaker, qué saga conviene a qué transacción. Lectores que ya no necesitan la definición de "back-pressure" pero que sí valoran ver tres implementaciones contrastadas de la misma idea.</p>',
    40
) ON CONFLICT (section_key) DO NOTHING;

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'audience_secondary',
    'Audiencia secundaria',
    '<p>Desarrolladoras y desarrolladores intermedios que ya leyeron la documentación de Akka, Pekko o un equivalente y ahora buscan saltar del ejemplo de manual al código que aguanta una guardia de producción. Para esta audiencia las piezas funcionan como puente: explican el porqué de las decisiones que el manual da por supuestas.</p>',
    50
) ON CONFLICT (section_key) DO NOTHING;

INSERT INTO editorial_identity (section_key, title, body_html, order_index)
VALUES (
    'we_are_not',
    'Lo que no somos',
    '<ul>'
    || '<li>No somos un tutorial introductorio de Scala, Akka ni Pekko.</li>'
    || '<li>No somos un changelog ni un canal de novedades de versiones.</li>'
    || '<li>No publicamos opinión sin un caso concreto que la sostenga.</li>'
    || '<li>No publicamos benchmarks aislados sin metodología verificable.</li>'
    || '</ul>',
    60
) ON CONFLICT (section_key) DO NOTHING;

# --- !Downs

DROP TABLE IF EXISTS editorial_identity;

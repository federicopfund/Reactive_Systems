# --- Documentos legales: privacidad y terminos servidos desde DB.
# --- Reemplaza app/views/legal/privacidad.scala.html y terminos.scala.html.

# --- !Ups

-- ============================================================
-- Tabla: legal_documents
--
-- Versionable por slug. La vista renderiza body_html tal cual.
-- last_updated_at es la fecha visible en la pieza ("Ultima actualizacion").
--
-- Permite que el equipo legal/editorial actualice copy sin deploy.
-- ============================================================

CREATE TABLE IF NOT EXISTS legal_documents (
    id              BIGSERIAL    PRIMARY KEY,
    slug            VARCHAR(100) NOT NULL UNIQUE,   -- 'privacidad', 'terminos'
    title           VARCHAR(255) NOT NULL,
    eyebrow         VARCHAR(100) NOT NULL DEFAULT 'Documento legal',
    body_html       TEXT         NOT NULL,
    last_updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_published    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- Seed: las 2 piezas legales actuales, migradas literalmente.
-- ============================================================

INSERT INTO legal_documents (slug, title, body_html, last_updated_at)
VALUES (
    'privacidad',
    'Política de privacidad',
    '<h2>1. Información que recopilamos</h2>'
    || '<p>Recolectamos los datos que tú nos das cuando creas una cuenta o nos contactas: nombre, correo electrónico y los mensajes que envías. También registramos datos básicos de navegación para entender cómo se usa la edición.</p>'
    || '<h2>2. Cómo usamos tus datos</h2>'
    || '<p>Tus datos se usan exclusivamente para operar la edición: autenticarte, mostrarte contenido relevante, responderte cuando escribes y publicar tus piezas si así lo decides. No vendemos información a terceros.</p>'
    || '<h2>3. Cookies</h2>'
    || '<p>Usamos cookies estrictamente necesarias para mantener tu sesión iniciada y recordar tu preferencia de tema. No usamos rastreo publicitario.</p>'
    || '<h2>4. Tus derechos</h2>'
    || '<p>Puedes acceder, corregir o eliminar tus datos en cualquier momento desde tu perfil o escribiéndonos a través del formulario de contacto.</p>'
    || '<h2>5. Contacto</h2>'
    || '<p>Si tienes dudas sobre esta política, escríbenos desde la <a href="/#contact">sección de contacto</a>.</p>',
    CURRENT_TIMESTAMP
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO legal_documents (slug, title, body_html, last_updated_at)
VALUES (
    'terminos',
    'Términos de uso',
    '<h2>1. Aceptación</h2>'
    || '<p>Al acceder a la edición y crear una cuenta aceptas estos términos. Si no estás de acuerdo, te pedimos no usar la plataforma.</p>'
    || '<h2>2. Uso aceptable</h2>'
    || '<p>El espacio existe para discutir software reactivo desde la práctica. Está prohibido publicar contenido ilegal, spam, contenido ofensivo o material protegido por derechos de autor sin permiso.</p>'
    || '<h2>3. Contenido del usuario</h2>'
    || '<p>Mantienes los derechos sobre lo que publicas. Al publicar, nos das una licencia no exclusiva para mostrarlo dentro de la edición. Puedes solicitar la baja en cualquier momento.</p>'
    || '<h2>4. Moderación editorial</h2>'
    || '<p>El equipo editorial revisa cada pieza antes de su publicación. Podemos rechazar o solicitar cambios, manteniendo siempre la coherencia con la línea editorial.</p>'
    || '<h2>5. Cambios a estos términos</h2>'
    || '<p>Podemos actualizar estos términos. Si los cambios son sustanciales, te avisaremos antes de que entren en vigor.</p>',
    CURRENT_TIMESTAMP
)
ON CONFLICT (slug) DO NOTHING;

# --- !Downs

DROP TABLE IF EXISTS legal_documents;

-- ============================================
-- TABLA DE PUBLICACIONES DE USUARIOS
-- ============================================

-- Eliminar tabla si existe (solo en desarrollo)
DROP TABLE IF EXISTS publications CASCADE;

-- Crear tabla de publicaciones
CREATE TABLE publications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(250) NOT NULL UNIQUE,
  content TEXT NOT NULL,
  excerpt VARCHAR(500),
  cover_image VARCHAR(500),
  category VARCHAR(100) NOT NULL,
  tags VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  view_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP,
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP,
  rejection_reason TEXT,
  
  -- Foreign keys
  CONSTRAINT fk_publication_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_publication_reviewer FOREIGN KEY (reviewed_by) REFERENCES admins(id) ON DELETE SET NULL,
  
  -- Constraints
  CONSTRAINT chk_status CHECK (status IN ('draft', 'pending', 'approved', 'rejected'))
);

-- Índices para mejorar el rendimiento
CREATE INDEX idx_publications_user_id ON publications(user_id);
CREATE INDEX idx_publications_status ON publications(status);
CREATE INDEX idx_publications_category ON publications(category);
CREATE INDEX idx_publications_slug ON publications(slug);
CREATE INDEX idx_publications_published_at ON publications(published_at DESC);
CREATE INDEX idx_publications_created_at ON publications(created_at DESC);

-- Trigger para actualizar updated_at automáticamente
CREATE OR REPLACE FUNCTION update_publication_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_publication_timestamp
BEFORE UPDATE ON publications
FOR EACH ROW
EXECUTE FUNCTION update_publication_timestamp();

-- ============================================
-- DATOS DE EJEMPLO (OPCIONAL)
-- ============================================

-- Insertar algunas publicaciones de ejemplo
-- Asumiendo que ya existen usuarios con id 1 y 2

INSERT INTO publications (user_id, title, slug, content, excerpt, category, tags, status, created_at) VALUES
(1, 'Introducción a Akka Actors', 'introduccion-akka-actors-1', 
 'Los actores en Akka son una abstracción fundamental para construir sistemas concurrentes, distribuidos y tolerantes a fallos...', 
 'Aprende los conceptos básicos de Akka Actors y cómo implementar el patrón de actores en Scala',
 'Scala', 'scala,akka,actors,concurrency', 'approved', NOW() - INTERVAL '10 days'),

(1, 'Patrones Reactivos con Play Framework', 'patrones-reactivos-play-framework-2',
 'Play Framework implementa el Reactive Manifesto proporcionando herramientas para construir aplicaciones responsivas...',
 'Descubre cómo Play Framework implementa los principios del Manifesto Reactivo',
 'Play Framework', 'play,reactive,scala', 'pending', NOW() - INTERVAL '2 days'),

(2, 'Gestión de Estado en Aplicaciones Reactivas', 'gestion-estado-aplicaciones-reactivas-3',
 'La gestión del estado es crucial en aplicaciones reactivas. En este artículo exploramos diferentes estrategias...',
 'Estrategias para manejar el estado en sistemas reactivos',
 'Arquitectura', 'reactive,state-management,patterns', 'draft', NOW() - INTERVAL '1 day');

-- Actualizar las publicaciones aprobadas con fecha de publicación
UPDATE publications SET published_at = updated_at WHERE status = 'approved';

-- Ver resumen de publicaciones
SELECT 
  u.username,
  p.title,
  p.status,
  p.category,
  p.created_at
FROM publications p
JOIN users u ON p.user_id = u.id
ORDER BY p.created_at DESC;

-- ============================================
-- CONSULTAS ÚTILES
-- ============================================

-- Contar publicaciones por estado
SELECT status, COUNT(*) as count
FROM publications
GROUP BY status;

-- Publicaciones más vistas
SELECT title, view_count, status
FROM publications
WHERE status = 'approved'
ORDER BY view_count DESC
LIMIT 10;

-- Usuarios con más publicaciones
SELECT u.username, COUNT(p.id) as total_publications
FROM users u
LEFT JOIN publications p ON u.id = p.user_id
GROUP BY u.username
ORDER BY total_publications DESC;

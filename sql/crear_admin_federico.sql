-- ================================================================================
-- CREAR ADMIN FEDERICO
-- ================================================================================
-- Fecha: 2026-01-09
-- Contraseña: 
-- ================================================================================

-- Ver admins actuales
SELECT id, username, email, role, created_at, last_login 
FROM admins 
ORDER BY created_at DESC;

-- Crear admin federico
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'federico',
    'federico@reactivemanifesto.com',
    '$2a$10$So8GceVpZX3J2ZX4ARqViuj9ldnk3uupjDGWGk9kReFufCpup3m1C',
    'admin'
);

-- Verificar que se creó correctamente
SELECT id, username, email, role, created_at 
FROM admins 
WHERE username = 'federico';

-- ================================================================================
-- INFORMACIÓN DE ACCESO
-- ================================================================================
-- URL de login: http://localhost:9000/admin/login
-- Usuario: federico
-- Contraseña: 
-- ================================================================================

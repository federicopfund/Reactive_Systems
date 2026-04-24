-- ================================================================================
-- SCRIPT PARA CREAR NUEVO ADMINISTRADOR
-- ================================================================================
-- INSTRUCCIONES:
-- 1. Genera un hash de contraseña:
--    cd /workspaces/Reactive-Manifiesto
--    sbt "runMain utils.PasswordHasher tuContraseña123"
--
-- 2. Copia el hash de la salida y reemplázalo en el INSERT de abajo
-- 
-- 3. Ejecuta este script en tu consola H2
-- ================================================================================

-- PASO 1: Ver administradores actuales
SELECT id, username, email, role, created_at, last_login 
FROM admins 
ORDER BY created_at DESC;

-- ================================================================================
-- PASO 2: CREAR NUEVO ADMINISTRADOR
-- ================================================================================
-- REEMPLAZA LOS VALORES SEGÚN TUS NECESIDADES:
--   - username: nombre de usuario único (ej: "admin", "superadmin", "franco")
--   - email: email único y válido
--   - password_hash: REEMPLAZA CON EL HASH GENERADO ARRIBA
--   - role: típicamente 'admin' (o usa 'superadmin' para permisos totales)
-- ================================================================================

-- Ejemplo: Crear admin "admin" con contraseña "admin123"
-- Hash generado de "admin123": $2a$10$N9qo8uLOickgx2ZMRZoMye4UT9FXP.3vZKGMsHJ.sP0hKgKpzjX6S
INSERT INTO admins (username, email, password_hash, role, created_at) 
VALUES (
    'admin',                                                                      -- Cambiar por tu username
    'admin@reactivemanifesto.com',                                               -- Cambiar por tu email
    '$2a$10$N9qo8uLOickgx2ZMRZoMye4UT9FXP.3vZKGMsHJ.sP0hKgKpzjX6S',           -- REEMPLAZAR con tu hash generado
    'admin',                                                                      -- Rol
    CURRENT_TIMESTAMP                                                             -- Fecha de creación
);

-- VERIFICAR QUE SE CREÓ CORRECTAMENTE
SELECT id, username, email, role, created_at 
FROM admins 
WHERE username = 'admin';

-- ================================================================================
-- INFORMACIÓN DE ACCESO
-- ================================================================================
-- URL de login: http://localhost:9000/auth/login
-- Tab: Administrador
-- Usuario: admin (o el username que hayas elegido)
-- Contraseña: admin123 (o la contraseña que hayas usado para generar el hash)
-- ================================================================================

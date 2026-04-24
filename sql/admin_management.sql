-- Script para gestionar administradores
-- =====================================

-- Ver todos los administradores
SELECT id, username, email, role, created_at, last_login 
FROM admins 
ORDER BY created_at DESC;

-- Crear nuevo administrador (cambiar valores)
-- Nota: El password_hash debe generarse con BCrypt
-- Usar: sbt "runMain utils.PasswordHasher tu_contraseña"
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'nuevo_admin',                                           -- Cambiar username
    'nuevo@example.com',                                     -- Cambiar email
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- Hash de "admin123"
    'admin'
);

-- Cambiar contraseña de un admin existente
-- Primero generar hash: sbt "runMain utils.PasswordHasher nueva_contraseña"
UPDATE admins 
SET password_hash = '$2a$10$TU_NUEVO_HASH_AQUI'
WHERE username = 'admin';

-- Actualizar email de un admin
UPDATE admins 
SET email = 'nuevo_email@example.com'
WHERE username = 'admin';

-- Eliminar un administrador
DELETE FROM admins 
WHERE username = 'nombre_usuario';

-- Ver último login de cada admin
SELECT username, email, last_login,
       CASE 
           WHEN last_login IS NULL THEN 'Nunca'
           ELSE to_char(last_login, 'DD/MM/YYYY HH24:MI:SS')
       END as ultimo_acceso
FROM admins
ORDER BY last_login DESC NULLS LAST;

-- Contar admins por rol
SELECT role, COUNT(*) as total
FROM admins
GROUP BY role;

-- Buscar admin por email
SELECT * FROM admins WHERE email LIKE '%@example.com%';

-- Verificar si hay admins
SELECT COUNT(*) as total_admins FROM admins;

-- Resetear last_login (para testing)
UPDATE admins SET last_login = NULL;

-- Crear admin de respaldo (emergency)
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'emergency_admin',
    'emergency@reactivemanifesto.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- admin123
    'admin'
);

-- Deshabilitar temporalmente (agregar campo active si lo implementas)
-- UPDATE admins SET active = false WHERE username = 'usuario';

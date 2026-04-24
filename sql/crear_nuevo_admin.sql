-- ================================================================================
-- SCRIPT PARA CREAR NUEVO ADMINISTRADOR
-- ================================================================================
-- Fecha: 2026-01-06
-- Hash generado para: TuContraseñaSegura123
-- ================================================================================

-- PASO 1: Ver administradores actuales
SELECT id, username, email, role, created_at, last_login 
FROM admins 
ORDER BY created_at DESC;

-- ================================================================================
-- PASO 2: CREAR NUEVO ADMINISTRADOR
-- ================================================================================
-- IMPORTANTE: Cambia los valores según tus necesidades:
--   - username: nombre de usuario único
--   - email: email único
--   - password_hash: el hash generado
--   - role: 'admin' (o 'superadmin', 'moderator', etc.)
-- ================================================================================

-- Ejemplo 1: Administrador principal
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'federico',                                                                  -- Tu nombre de usuario
    'federico@reactivemanifesto.com',                                           -- Tu email
    '$2a$10$r1ogprmwixoGhGgw4sv0fuzU.Prn2JhdWphK5SEgl7Fp95.1KGHHG',           -- Hash de "TuContraseñaSegura123"
    'admin'                                                                      -- Rol
);

-- Ejemplo 2: Super Administrador
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'superadmin',
    'super@reactivemanifesto.com',
    '$2a$10$r1ogprmwixoGhGgw4sv0fuzU.Prn2JhdWphK5SEgl7Fp95.1KGHHG',
    'superadmin'
);

-- Ejemplo 3: Administrador de respaldo
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'admin_backup',
    'backup@reactivemanifesto.com',
    '$2a$10$r1ogprmwixoGhGgw4sv0fuzU.Prn2JhdWphK5SEgl7Fp95.1KGHHG',
    'admin'
);

-- ================================================================================
-- PASO 3: Verificar que se creó correctamente
-- ================================================================================
SELECT username, email, role, created_at 
FROM admins 
WHERE username = 'federico';  -- Cambia por tu username

-- ================================================================================
-- OTRAS OPERACIONES ÚTILES
-- ================================================================================

-- Ver todos los administradores
SELECT id, username, email, role, 
       CASE 
           WHEN last_login IS NULL THEN 'Nunca ha ingresado'
           ELSE CAST(last_login AS VARCHAR)
       END as ultimo_acceso
FROM admins
ORDER BY created_at DESC;

-- Cambiar contraseña de un admin existente
-- (Primero genera el hash con: sbt "runMain utils.PasswordHasher nueva_contraseña")
UPDATE admins 
SET password_hash = '$2a$10$NUEVO_HASH_AQUI'
WHERE username = 'federico';

-- Cambiar email
UPDATE admins 
SET email = 'nuevo_email@example.com'
WHERE username = 'federico';

-- Cambiar rol
UPDATE admins 
SET role = 'superadmin'
WHERE username = 'federico';

-- Eliminar un administrador (¡CUIDADO!)
DELETE FROM admins 
WHERE username = 'admin_a_eliminar';

-- ================================================================================
-- INFORMACIÓN IMPORTANTE
-- ================================================================================

-- Credenciales creadas:
-- Usuario: federico (o el que hayas elegido)
-- Contraseña: TuContraseñaSegura123
-- Email: federico@reactivemanifesto.com

-- Para hacer login:
-- 1. Ir a: http://localhost:9000/login
-- 2. Seleccionar tab "Administrador"
-- 3. Ingresar credenciales
-- 4. Acceso a: http://localhost:9000/admin/dashboard

-- ================================================================================
-- GENERAR NUEVO HASH DE CONTRASEÑA
-- ================================================================================
-- Ejecutar en terminal:
-- sbt "runMain utils.PasswordHasher tu_contraseña_deseada"
-- 
-- Copiar el hash generado y usarlo en los INSERT/UPDATE

-- ================================================================================
-- SEGURIDAD - RECOMENDACIONES
-- ================================================================================
-- 1. Usa contraseñas fuertes (mínimo 12 caracteres)
-- 2. No reutilices contraseñas
-- 3. Considera usar gestores de contraseñas
-- 4. En producción, cambia todas las contraseñas por defecto
-- 5. Habilita 2FA si es posible
-- 6. Revisa regularmente los accesos de administradores

-- ================================================================================

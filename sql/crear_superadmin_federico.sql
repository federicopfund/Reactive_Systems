-- ================================================================================
-- CREAR / ACTUALIZAR SUPER ADMIN: federico
-- ================================================================================
-- Fecha: 2026-05-09
-- Rol: super_admin
-- ================================================================================

-- Insertar o actualizar el usuario federico como super_admin.
-- Si el username ya existe, actualiza el hash y promueve a super_admin.
INSERT INTO users (
    username,
    email,
    password_hash,
    full_name,
    role,
    is_active,
    email_verified,
    admin_approved,
    created_at
)
VALUES (
    'federico',
    'federico@reactivemanifesto.com',
    '$2a$10$xemWZ9g5VO8FMOgYIEyZIe.qXDqcT063kAaoikOUyoHIwR7aDEC8C',
    'Federico',
    'super_admin',
    true,
    true,
    true,
    NOW()
)
ON CONFLICT (username) DO UPDATE
    SET password_hash   = EXCLUDED.password_hash,
        role            = 'super_admin',
        is_active       = true,
        email_verified  = true,
        admin_approved  = true;

-- Verificar resultado
SELECT id, username, email, role, is_active, admin_approved, email_verified, created_at
FROM users
WHERE username = 'federico';

-- ================================================================================
-- INFORMACIÓN DE ACCESO
-- ================================================================================
-- URL de login : http://localhost:9000/admin/login
-- Usuario      : federico
-- Contraseña   : Fede/(40021)
-- Rol          : super_admin
-- ================================================================================

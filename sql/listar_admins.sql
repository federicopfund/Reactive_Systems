-- ================================================================================
-- LISTAR TODOS LOS ADMINISTRADORES
-- ================================================================================

SELECT 
  id,
  username,
  email,
  role,
  created_at,
  last_login
FROM admins
ORDER BY created_at DESC;

-- ================================================================================
-- ESTADÍSTICAS
-- ================================================================================

-- Contar total de admins
SELECT COUNT(*) as total_admins FROM admins;

-- Ver si existen admins
SELECT 
  CASE WHEN COUNT(*) > 0 THEN 'SÍ existen admins' ELSE 'NO hay admins' END as resultado
FROM admins;

-- ================================================================================

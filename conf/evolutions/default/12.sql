# --- !Ups

-- Agregar campos de administración al table users
-- admin_approved: si el admin fue aprobado por un super_admin
-- admin_approved_by: ID del super_admin que aprobó
-- admin_requested_at: cuándo solicitó ser admin
ALTER TABLE users ADD COLUMN admin_approved BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN admin_approved_by BIGINT REFERENCES users(id);
ALTER TABLE users ADD COLUMN admin_requested_at TIMESTAMP;

-- Limpiar la tabla admins (ya no se usa, toda la lógica va por users)
DELETE FROM admins;

# --- !Downs

ALTER TABLE users DROP COLUMN IF EXISTS admin_requested_at;
ALTER TABLE users DROP COLUMN IF EXISTS admin_approved_by;
ALTER TABLE users DROP COLUMN IF EXISTS admin_approved;

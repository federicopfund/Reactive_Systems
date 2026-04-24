# --- !Ups

CREATE TABLE admins (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'admin',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

CREATE INDEX idx_admins_username ON admins(username);
CREATE INDEX idx_admins_email ON admins(email);

-- Insertar usuario admin por defecto
-- Usuario: admin, Contraseña: admin123 (cambiar en producción)
-- Hash BCrypt de "admin123"
INSERT INTO admins (username, email, password_hash, role) 
VALUES ('admin', 'admin@reactivemanifesto.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin');

# --- !Downs

DROP INDEX IF EXISTS idx_admins_email;
DROP INDEX IF EXISTS idx_admins_username;
DROP TABLE IF EXISTS admins;

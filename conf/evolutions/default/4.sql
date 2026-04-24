# --- !Ups

-- Crear tu administrador personalizado
INSERT INTO admins (username, email, password_hash, role) 
VALUES (
    'federico',
    'federico@reactivemanifesto.com',
    '$2a$10$r1ogprmwixoGhGgw4sv0fuzU.Prn2JhdWphK5SEgl7Fp95.1KGHHG',
    'admin'
);

# --- !Downs

DELETE FROM admins WHERE username = 'federico';

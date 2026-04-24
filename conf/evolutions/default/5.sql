# --- !Ups

-- Tabla para códigos de verificación por email
CREATE TABLE email_verification_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_verification_user_id ON email_verification_codes(user_id);
CREATE INDEX idx_verification_code ON email_verification_codes(code);
CREATE INDEX idx_verification_expires ON email_verification_codes(expires_at);

-- Agregar columna email_verified a users
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

# --- !Downs

ALTER TABLE users DROP COLUMN email_verified;
DROP INDEX IF EXISTS idx_verification_expires;
DROP INDEX IF EXISTS idx_verification_code;
DROP INDEX IF EXISTS idx_verification_user_id;
DROP TABLE IF EXISTS email_verification_codes;

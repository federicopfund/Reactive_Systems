-- !Ups

-- Issue #21 — Configuración runtime de los agentes (admin)
-- Settings persistentes (key/value tipado) y auditoría inmutable de cambios.

CREATE TABLE IF NOT EXISTS agent_settings (
  setting_key      VARCHAR(120) PRIMARY KEY,
  value_text       TEXT         NOT NULL,
  value_type       VARCHAR(16)  NOT NULL,             -- 'int' | 'long' | 'bool' | 'string' | 'duration'
  category         VARCHAR(60)  NOT NULL,             -- 'supervision' | 'backoff' | 'heartbeat' | 'cb' | 'pipeline' | 'engines' | 'dashboard'
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT       NULL                  -- admin user id
);

CREATE TABLE IF NOT EXISTS agent_settings_audit (
  id               BIGSERIAL    PRIMARY KEY,
  setting_key      VARCHAR(120) NOT NULL,
  old_value        TEXT         NULL,
  new_value        TEXT         NOT NULL,
  changed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  changed_by       BIGINT       NULL,
  changed_by_name  VARCHAR(120) NULL,
  reason           VARCHAR(60)  NOT NULL              -- 'set' | 'reset'
);

CREATE INDEX IF NOT EXISTS idx_agent_settings_audit_key  ON agent_settings_audit(setting_key);
CREATE INDEX IF NOT EXISTS idx_agent_settings_audit_when ON agent_settings_audit(changed_at DESC);

-- !Downs
DROP TABLE IF EXISTS agent_settings_audit;
DROP TABLE IF EXISTS agent_settings;

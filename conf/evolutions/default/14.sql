# --- !Ups

CREATE TABLE IF NOT EXISTS private_messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    publication_id BIGINT REFERENCES publications(id) ON DELETE SET NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pm_receiver ON private_messages(receiver_id, created_at DESC);
CREATE INDEX idx_pm_sender ON private_messages(sender_id, created_at DESC);
CREATE INDEX idx_pm_publication ON private_messages(publication_id);

# --- !Downs

DROP TABLE IF EXISTS private_messages;

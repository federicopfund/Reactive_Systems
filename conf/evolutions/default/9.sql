# --- Notificaciones para usuarios

# --- !Ups

CREATE TABLE user_notifications (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(30) NOT NULL DEFAULT 'feedback_sent',
    title             VARCHAR(200) NOT NULL,
    message           TEXT NOT NULL,
    publication_id    BIGINT REFERENCES publications(id) ON DELETE SET NULL,
    feedback_id       BIGINT REFERENCES publication_feedback(id) ON DELETE SET NULL,
    is_read           BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_user ON user_notifications(user_id);
CREATE INDEX idx_notif_unread ON user_notifications(user_id, is_read);

# --- !Downs

DROP TABLE IF EXISTS user_notifications;

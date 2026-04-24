# --- Feedback estructurado de admin a usuario

# --- !Ups

CREATE TABLE publication_feedback (
    id              BIGSERIAL PRIMARY KEY,
    publication_id  BIGINT NOT NULL REFERENCES publications(id) ON DELETE CASCADE,
    admin_id        BIGINT NOT NULL REFERENCES users(id),
    feedback_type   VARCHAR(30) NOT NULL DEFAULT 'general',
    message         TEXT NOT NULL,
    sent_to_user    BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feedback_publication ON publication_feedback(publication_id);
CREATE INDEX idx_feedback_sent ON publication_feedback(publication_id, sent_to_user);

# --- !Downs

DROP TABLE IF EXISTS publication_feedback;

# --- !Ups

CREATE TABLE newsletter_subscribers (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    subscribed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    unsubscribed_at TIMESTAMP,
    ip_address VARCHAR(45),
    CONSTRAINT uq_newsletter_email UNIQUE(email)
);

CREATE INDEX idx_newsletter_active ON newsletter_subscribers(is_active) WHERE is_active = TRUE;

# --- !Downs

DROP TABLE IF EXISTS newsletter_subscribers;

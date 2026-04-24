# --- !Ups

-- ============================================
-- REACTIONS / LIKES
-- ============================================
CREATE TABLE publication_reactions (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(20) NOT NULL DEFAULT 'like',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reaction_user_pub UNIQUE (publication_id, user_id, reaction_type)
);

CREATE INDEX idx_reactions_publication ON publication_reactions(publication_id);
CREATE INDEX idx_reactions_user ON publication_reactions(user_id);

-- ============================================
-- COMMENTS
-- ============================================
CREATE TABLE publication_comments (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_publication ON publication_comments(publication_id);
CREATE INDEX idx_comments_user ON publication_comments(user_id);

-- ============================================
-- BOOKMARKS
-- ============================================
CREATE TABLE user_bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    publication_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bookmark_user_pub UNIQUE (user_id, publication_id)
);

CREATE INDEX idx_bookmarks_user ON user_bookmarks(user_id);
CREATE INDEX idx_bookmarks_publication ON user_bookmarks(publication_id);

-- ============================================
-- USER PROFILES (extended fields)
-- ============================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500) DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS website VARCHAR(300) DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS location VARCHAR(100) DEFAULT '';

-- ============================================
-- GAMIFICATION: BADGES
-- ============================================
CREATE TABLE user_badges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_key VARCHAR(50) NOT NULL,
    awarded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_badge_user UNIQUE (user_id, badge_key)
);

CREATE INDEX idx_badges_user ON user_badges(user_id);

# --- !Downs

DROP TABLE IF EXISTS user_badges;
ALTER TABLE users DROP COLUMN IF EXISTS bio;
ALTER TABLE users DROP COLUMN IF EXISTS avatar_url;
ALTER TABLE users DROP COLUMN IF EXISTS website;
ALTER TABLE users DROP COLUMN IF EXISTS location;
DROP TABLE IF EXISTS user_bookmarks;
DROP TABLE IF EXISTS publication_comments;
DROP TABLE IF EXISTS publication_reactions;

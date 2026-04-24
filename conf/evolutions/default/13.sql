# --- Fix reviewed_by FK: admins → users

# --- !Ups

-- The reviewed_by column incorrectly references admins(id),
-- but all auth logic uses the users table (since evolution 12).
-- This causes a FK violation when an admin (from users table) approves a publication.
ALTER TABLE publications DROP CONSTRAINT fk_publication_reviewer;
ALTER TABLE publications ADD CONSTRAINT fk_publication_reviewer
  FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

# --- !Downs

ALTER TABLE publications DROP CONSTRAINT fk_publication_reviewer;
ALTER TABLE publications ADD CONSTRAINT fk_publication_reviewer
  FOREIGN KEY (reviewed_by) REFERENCES admins(id) ON DELETE SET NULL;

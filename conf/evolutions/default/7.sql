# --- Add admin notes to publications

# --- !Ups

ALTER TABLE publications ADD COLUMN admin_notes TEXT;

# --- !Downs

ALTER TABLE publications DROP COLUMN admin_notes;

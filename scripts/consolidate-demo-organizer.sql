-- Data-only, idempotent update for the original 25 demonstration events.
-- Run in the application database/schema; do not apply to later real events.
UPDATE events
SET organizer_email = 'org@connectify.com'
WHERE id BETWEEN 1 AND 25
  AND (organizer_email IS NULL
       OR LOWER(organizer_email) IN (
           'org@connectify.com',
           'organizador.altura@connectify.local',
           'organizador.cultural@connectify.local',
           'organizador.demo@connectify.local'
       ));

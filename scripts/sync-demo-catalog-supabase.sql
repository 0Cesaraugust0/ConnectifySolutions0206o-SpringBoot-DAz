-- Align the original demonstration catalog with the already reviewed local version.
-- This script changes demo ownership, two demo dates and one demo publication status.
-- It never changes ticket prices, inventory, ticket types, sales or purchases.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM connectify.events
        WHERE id = 1 AND title = 'Festival de Rock Lima 2025'
          AND event_date IN (TIMESTAMP '2026-10-19 19:30:36', TIMESTAMP '2026-10-26 19:30:51')
    ) THEN
        RAISE EXCEPTION 'Demo event 1 changed since review';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM connectify.events
        WHERE id = 22 AND title = 'Encuentro Musical'
          AND event_date IN (TIMESTAMP '2026-10-15 02:06:00', TIMESTAMP '2026-10-22 19:30:21')
    ) THEN
        RAISE EXCEPTION 'Demo event 22 changed since review';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM connectify.events
        WHERE id = 12 AND title = 'Conferencia Impulsa Negocios'
          AND status IN ('PENDING_REVIEW', 'PUBLISHED')
    ) THEN
        RAISE EXCEPTION 'Demo event 12 changed since review';
    END IF;
    IF EXISTS (
        SELECT 1 FROM connectify.purchases WHERE event_id IN (1, 12, 22)
    ) THEN
        RAISE EXCEPTION 'A demo event has a recorded purchase; review schedule/publication manually';
    END IF;
END $$;

UPDATE connectify.events
SET organizer_email = 'org@connectify.com', updated_at = now()
WHERE id BETWEEN 1 AND 25
  AND (organizer_email IS NULL
       OR lower(organizer_email) IN (
           'organizador.altura@connectify.local',
           'organizador.cultural@connectify.local',
           'organizador.demo@connectify.local'
       ));

UPDATE connectify.events
SET event_date = TIMESTAMP '2026-10-26 19:30:51', updated_at = now()
WHERE id = 1 AND title = 'Festival de Rock Lima 2025';

UPDATE connectify.events
SET event_date = TIMESTAMP '2026-10-22 19:30:21', updated_at = now()
WHERE id = 22 AND title = 'Encuentro Musical';

UPDATE connectify.events
SET status = 'PUBLISHED', updated_at = now()
WHERE id = 12 AND title = 'Conferencia Impulsa Negocios';

DO $$
BEGIN
    IF (SELECT count(*) FROM connectify.events
        WHERE id IN (1, 8, 9, 10, 12, 22)
          AND organizer_email = 'org@connectify.com') <> 6 THEN
        RAISE EXCEPTION 'Demo ownership did not align; rolling back';
    END IF;
END $$;

COMMIT;

SELECT id, title, status, organizer_email, event_date
FROM connectify.events
WHERE id IN (1, 8, 9, 10, 12, 22)
ORDER BY event_date;

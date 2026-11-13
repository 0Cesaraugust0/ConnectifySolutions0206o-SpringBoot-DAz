SET lock_timeout = '5s';
ALTER TABLE connectify.event_presentation_settings
    ADD COLUMN IF NOT EXISTS promotional_image_url text,
    ADD COLUMN IF NOT EXISTS tour_event_ids varchar(120);

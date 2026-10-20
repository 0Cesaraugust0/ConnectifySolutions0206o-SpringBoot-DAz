-- Presentation-only synchronization for five original demo events.
-- Apply after the ten creative-* assets are present in the deployed JAR.
-- This deliberately leaves event status, date, ownership, price, capacity,
-- ticket inventory, purchases and sales unchanged. Apply
-- sync-demo-catalog-supabase.sql separately to align demo ownership, two dates
-- and one publication state with the local marketplace order.
-- PostgreSQL / Supabase private connectify schema. Safe to re-run.
BEGIN;

DO $guard$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM (VALUES
            (8, 'Tech Summit Perú'),
            (9, 'Festival Gastronómico Sabores del Perú'),
            (10, 'Final Copa Interbarrios'),
            (12, 'Conferencia Impulsa Negocios'),
            (22, 'Encuentro Musical')
        ) AS expected(id, title)
        JOIN connectify.events e ON e.id = expected.id AND e.title = expected.title
    ) <> 5 THEN
        RAISE EXCEPTION 'Creative demo event IDs/titles do not match production; no changes applied';
    END IF;
END;
$guard$;

UPDATE connectify.events e
SET description = d.description,
    design_template = d.design_template,
    image_url = d.image_url,
    updated_at = NOW()
FROM jsonb_populate_recordset(
    NULL::connectify.events,
    $events_json$[{"id":8,"title":"Tech Summit Perú","description":"Ideas de inteligencia artificial, nube y desarrollo web reunidas en una experiencia de charlas y demostraciones. Una muestra de cómo presentar un congreso tecnológico con una portada de campaña. Programa demostrativo y referencial.","design_template":"PROMO_DETAIL","image_url":"/images/showcase/creative-8-cover.png"},{"id":9,"title":"Festival Gastronómico Sabores del Perú","description":"Sabores, cocina abierta y encuentro alrededor de la mesa peruana. La propuesta combina estaciones gastronómicas, demostraciones y un espacio para compartir. Actividades y distribución referenciales de esta demostración.","design_template":"STAGE_MEDIA","image_url":"/images/showcase/creative-9-cover.png"},{"id":10,"title":"Final Copa Interbarrios","description":"Una final de fútbol barrial con tribunas, ambiente familiar y acceso mediante entrada digital. Esta ficha muestra un diseño orientado a la información de ingreso y a la disponibilidad de entradas. Programa demostrativo.","design_template":"TICKET_PANEL","image_url":"/images/showcase/creative-10-cover.png"},{"id":12,"title":"Conferencia Impulsa Negocios","description":"Una velada de conversación sobre ideas, gestión y crecimiento de negocios. La presentación editorial destaca ponencias, intercambio y una experiencia ejecutiva clara. Ponentes y programa son referenciales de esta demostración.","design_template":"IMMERSIVE","image_url":"/images/showcase/creative-12-cover.png"},{"id":22,"title":"Encuentro Musical","description":"Una noche de voces, bandas y luz en un escenario compartido. Recorre una propuesta musical pensada para disfrutar desde la apertura hasta el cierre. Evento demostrativo: artistas, programa y distribución son referenciales.","design_template":"POSTER_LANDING","image_url":"/images/showcase/creative-22-cover.png"}]$events_json$::jsonb
) AS d
WHERE e.id = d.id AND e.title = d.title
  AND e.id IN (8, 9, 10, 12, 22);

WITH expected(event_id, title) AS (
    VALUES
        (8, 'Tech Summit Perú'),
        (9, 'Festival Gastronómico Sabores del Perú'),
        (10, 'Final Copa Interbarrios'),
        (12, 'Conferencia Impulsa Negocios'),
        (22, 'Encuentro Musical')
)
INSERT INTO connectify.event_presentation_settings (
    event_id, primary_color, accent_color, highlight_text, cover_image_url, thumbnail_image_url, background_image_url, stage_image_url, organizer_brand_name, organizer_logo_url, cover_focus, content_focus, info_position, show_gallery, show_organizer, show_agenda, agenda_text, show_benefits, benefits_text, show_sponsors, sponsors_text, sponsor_logos_text, show_media, youtube_url, spotify_url, show_stage_map, stage_text, show_sessions, session_text, updated_at
)
SELECT d.event_id, d.primary_color, d.accent_color, d.highlight_text, d.cover_image_url, d.thumbnail_image_url, d.background_image_url, d.stage_image_url, d.organizer_brand_name, d.organizer_logo_url, d.cover_focus, d.content_focus, d.info_position, d.show_gallery, d.show_organizer, d.show_agenda, d.agenda_text, d.show_benefits, d.benefits_text, d.show_sponsors, d.sponsors_text, d.sponsor_logos_text, d.show_media, d.youtube_url, d.spotify_url, d.show_stage_map, d.stage_text, d.show_sessions, d.session_text, NOW()
FROM jsonb_populate_recordset(
    NULL::connectify.event_presentation_settings,
    $presentation_json$[{"event_id":8,"primary_color":"#0D2942","accent_color":"#36C6DD","highlight_text":"TECH SUMMIT · Ideas aplicadas, conversaciones reales · Demostración","cover_image_url":"/images/showcase/creative-8-cover.png","thumbnail_image_url":"/images/showcase/creative-8-cover.png","background_image_url":null,"stage_image_url":"/images/showcase/creative-8-stage.svg","organizer_brand_name":"Conexión Tech · Demo","organizer_logo_url":null,"cover_focus":"CENTER","content_focus":"INFORMATION","info_position":"STANDARD","show_gallery":true,"show_organizer":true,"show_agenda":true,"agenda_text":"19:00 · Acreditación y orientación\n19:30 · Apertura del encuentro\n20:00 · Panorama de inteligencia artificial aplicada\n20:45 · Conversación sobre nube y desarrollo web\n21:30 · Demostraciones y preguntas\n22:00 · Cierre referencial","show_benefits":true,"benefits_text":"Acceso a las charlas y zona de demostración.\nEspacio de preguntas y conversación entre asistentes.\nAgenda y distribución de salas sujetas a confirmación; contenido demostrativo.","show_sponsors":false,"sponsors_text":null,"sponsor_logos_text":null,"show_media":false,"youtube_url":null,"spotify_url":null,"show_stage_map":true,"stage_text":"Ilustración conceptual del escenario, el público y los temas de la conferencia. No representa butacas ni distribución real.","show_sessions":false,"session_text":null},{"event_id":9,"primary_color":"#6A3D26","accent_color":"#EA9C51","highlight_text":"SABORES DEL PERÚ · Cocina abierta y encuentro · Propuesta de demostración","cover_image_url":"/images/showcase/creative-9-cover.png","thumbnail_image_url":"/images/showcase/creative-9-cover.png","background_image_url":null,"stage_image_url":"/images/showcase/creative-9-stage.svg","organizer_brand_name":"Sabores del Perú · Demo","organizer_logo_url":null,"cover_focus":"CENTER","content_focus":"INFORMATION","info_position":"STANDARD","show_gallery":true,"show_organizer":true,"show_agenda":true,"agenda_text":"19:00 · Apertura de accesos\n19:30 · Inicio del recorrido gastronómico\n20:15 · Cocina abierta: ingredientes de temporada\n21:00 · Conversación sobre recetas y tradición\n21:45 · Último recorrido y cierre referencial","show_benefits":true,"benefits_text":"General: acceso a stands, música en vivo y degustaciones según la entrada vigente.\nVIP: acceso preferente y cata guiada según la entrada vigente.\nOtros consumos o servicios requieren confirmación del organizador.","show_sponsors":false,"sponsors_text":null,"sponsor_logos_text":null,"show_media":false,"youtube_url":null,"spotify_url":null,"show_stage_map":true,"stage_text":"Ilustración temática de cocina, stands, degustación y música. No confirma ubicación de puestos, sectores ni aforo.","show_sessions":false,"session_text":null},{"event_id":10,"primary_color":"#173A48","accent_color":"#B8D75A","highlight_text":"FINAL COPA INTERBARRIOS · La emoción se vive desde la tribuna · Demo","cover_image_url":"/images/showcase/creative-10-cover.png","thumbnail_image_url":"/images/showcase/creative-10-cover.png","background_image_url":null,"stage_image_url":"/images/showcase/creative-10-stage.svg","organizer_brand_name":"Copa Interbarrios · Demo","organizer_logo_url":null,"cover_focus":"CENTER","content_focus":"SALES","info_position":"STANDARD","show_gallery":true,"show_organizer":true,"show_agenda":true,"agenda_text":"18:30 · Acceso y orientación de tribunas\n19:30 · Inicio del partido\n20:15 · Descanso referencial\n21:30 · Cierre y salida ordenada","show_benefits":true,"benefits_text":"Ambiente de partido, campo de juego y público en la tribuna.\nLas entradas digitales se validarán en el ingreso cuando se publiquen sus variantes.\nNo hay butacas ni sectores confirmados en esta demostración.","show_sponsors":false,"sponsors_text":null,"sponsor_logos_text":null,"show_media":false,"youtube_url":null,"spotify_url":null,"show_stage_map":true,"stage_text":"Ilustración conceptual del campo y el ambiente de tribuna. Los accesos, sectores y lugares deben confirmarse por el organizador.","show_sessions":false,"session_text":null},{"event_id":12,"primary_color":"#122B39","accent_color":"#D6A86A","highlight_text":"IMPULSA NEGOCIOS · Conversaciones que abren posibilidades · Demo","cover_image_url":"/images/showcase/creative-12-cover.png","thumbnail_image_url":"/images/showcase/creative-12-cover.png","background_image_url":"/images/showcase/creative-12-cover.png","stage_image_url":"/images/showcase/creative-12-stage.svg","organizer_brand_name":"Impulsa · Demo","organizer_logo_url":null,"cover_focus":"CENTER","content_focus":"INFORMATION","info_position":"STANDARD","show_gallery":true,"show_organizer":true,"show_agenda":true,"agenda_text":"19:00 · Registro y bienvenida\n19:30 · Apertura de la conferencia\n20:00 · Conversación: de la idea a la ejecución\n20:45 · Panel de experiencias empresariales\n21:30 · Preguntas y encuentro entre asistentes\n22:00 · Cierre referencial","show_benefits":true,"benefits_text":"Ingreso a la conferencia y a los espacios comunes.\nConversación abierta al final de la sesión.\nPonentes, contenidos y servicios adicionales son referenciales en esta demostración.","show_sponsors":false,"sponsors_text":null,"sponsor_logos_text":null,"show_media":false,"youtube_url":null,"spotify_url":null,"show_stage_map":true,"stage_text":"Ilustración conceptual de escenario, ponencias y público. No indica butacas, sectores ni accesos definitivos.","show_sessions":false,"session_text":null},{"event_id":22,"primary_color":"#24143D","accent_color":"#EFA447","highlight_text":"ESCENA EN VIVO · Una noche de voces y luz · Programa referencial","cover_image_url":"/images/showcase/creative-22-cover.png","thumbnail_image_url":"/images/showcase/creative-22-cover.png","background_image_url":null,"stage_image_url":"/images/showcase/creative-22-stage.svg","organizer_brand_name":"Escena Lima · Demo","organizer_logo_url":null,"cover_focus":"CENTER","content_focus":"INFORMATION","info_position":"STANDARD","show_gallery":true,"show_organizer":true,"show_agenda":true,"agenda_text":"19:00 · Apertura de puertas y orientación\n19:30 · Inicio de la experiencia musical\n20:15 · Encuentro de voces\n21:00 · Bloque de bandas y escena\n22:00 · Cierre referencial","show_benefits":true,"benefits_text":"General: acceso general al evento.\nPlatinium: acceso preferente y beneficios adicionales según las condiciones de esa entrada.\nLa ilustración de escenario no representa sectores ni butacas asignadas.","show_sponsors":false,"sponsors_text":null,"sponsor_logos_text":null,"show_media":false,"youtube_url":null,"spotify_url":null,"show_stage_map":true,"stage_text":"Ilustración conceptual del escenario y el ambiente del público. No confirma accesos, sectores, asientos ni distribución real.","show_sessions":false,"session_text":null}]$presentation_json$::jsonb
) AS d
JOIN expected x ON x.event_id = d.event_id
JOIN connectify.events e ON e.id = x.event_id AND e.title = x.title
WHERE d.event_id IN (8, 9, 10, 12, 22)
ON CONFLICT (event_id) DO UPDATE SET
    primary_color = EXCLUDED.primary_color,
    accent_color = EXCLUDED.accent_color,
    highlight_text = EXCLUDED.highlight_text,
    cover_image_url = EXCLUDED.cover_image_url,
    thumbnail_image_url = EXCLUDED.thumbnail_image_url,
    background_image_url = EXCLUDED.background_image_url,
    stage_image_url = EXCLUDED.stage_image_url,
    organizer_brand_name = EXCLUDED.organizer_brand_name,
    organizer_logo_url = EXCLUDED.organizer_logo_url,
    cover_focus = EXCLUDED.cover_focus,
    content_focus = EXCLUDED.content_focus,
    info_position = EXCLUDED.info_position,
    show_gallery = EXCLUDED.show_gallery,
    show_organizer = EXCLUDED.show_organizer,
    show_agenda = EXCLUDED.show_agenda,
    agenda_text = EXCLUDED.agenda_text,
    show_benefits = EXCLUDED.show_benefits,
    benefits_text = EXCLUDED.benefits_text,
    show_sponsors = EXCLUDED.show_sponsors,
    sponsors_text = EXCLUDED.sponsors_text,
    sponsor_logos_text = EXCLUDED.sponsor_logos_text,
    show_media = EXCLUDED.show_media,
    youtube_url = EXCLUDED.youtube_url,
    spotify_url = EXCLUDED.spotify_url,
    show_stage_map = EXCLUDED.show_stage_map,
    stage_text = EXCLUDED.stage_text,
    show_sessions = EXCLUDED.show_sessions,
    session_text = EXCLUDED.session_text,
    updated_at = EXCLUDED.updated_at;

COMMIT;

-- Verify the five designs and report the current publication dates/states.
SELECT e.id, e.title, e.status, e.event_date,
       e.design_template, e.image_url,
       p.cover_image_url, p.stage_image_url,
       p.show_agenda, p.show_benefits, p.show_stage_map
FROM connectify.events e
JOIN connectify.event_presentation_settings p ON p.event_id = e.id
WHERE e.id IN (8, 9, 10, 12, 22)
ORDER BY e.id;

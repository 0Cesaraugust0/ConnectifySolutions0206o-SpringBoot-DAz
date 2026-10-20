-- Local demo art for card positions 1, 3, 4, 5 and 6 on /events.
-- Event IDs: 22, 8, 9, 10 and 12. Re-runnable; leaves sales, ticket types,
-- event dates, status, capacity and prices untouched.
START TRANSACTION;

UPDATE events SET design_template = 'POSTER_LANDING',
    image_url = '/images/showcase/creative-22-cover.png',
    description = 'Una noche de voces, bandas y luz en un escenario compartido. Recorre una propuesta musical pensada para disfrutar desde la apertura hasta el cierre. Evento demostrativo: artistas, programa y distribución son referenciales.',
    updated_at = NOW()
WHERE id = 22 AND organizer_email = 'org@connectify.com';

UPDATE events SET design_template = 'PROMO_DETAIL',
    image_url = '/images/showcase/creative-8-cover.png',
    description = 'Ideas de inteligencia artificial, nube y desarrollo web reunidas en una experiencia de charlas y demostraciones. Una muestra de cómo presentar un congreso tecnológico con una portada de campaña. Programa demostrativo y referencial.',
    updated_at = NOW()
WHERE id = 8 AND organizer_email = 'org@connectify.com';

UPDATE events SET design_template = 'STAGE_MEDIA',
    image_url = '/images/showcase/creative-9-cover.png',
    description = 'Sabores, cocina abierta y encuentro alrededor de la mesa peruana. La propuesta combina estaciones gastronómicas, demostraciones y un espacio para compartir. Actividades y distribución referenciales de esta demostración.',
    updated_at = NOW()
WHERE id = 9 AND organizer_email = 'org@connectify.com';

UPDATE events SET design_template = 'TICKET_PANEL',
    image_url = '/images/showcase/creative-10-cover.png',
    description = 'Una final de fútbol barrial con tribunas, ambiente familiar y acceso mediante entrada digital. Esta ficha muestra un diseño orientado a la información de ingreso y a la disponibilidad de entradas. Programa demostrativo.',
    updated_at = NOW()
WHERE id = 10 AND organizer_email = 'org@connectify.com';

UPDATE events SET design_template = 'IMMERSIVE',
    image_url = '/images/showcase/creative-12-cover.png',
    description = 'Una velada de conversación sobre ideas, gestión y crecimiento de negocios. La presentación editorial destaca ponencias, intercambio y una experiencia ejecutiva clara. Ponentes y programa son referenciales de esta demostración.',
    updated_at = NOW()
WHERE id = 12 AND organizer_email = 'org@connectify.com';

INSERT INTO event_presentation_settings
    (event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,
     background_image_url,stage_image_url,organizer_brand_name,organizer_logo_url,
     cover_focus,content_focus,info_position,show_gallery,show_organizer,
     show_agenda,agenda_text,show_benefits,benefits_text,show_sponsors,sponsors_text,
     sponsor_logos_text,show_media,youtube_url,spotify_url,show_stage_map,stage_text,
     show_sessions,session_text,updated_at)
SELECT 22,'#24143D','#EFA447',
    'ESCENA EN VIVO · Una noche de voces y luz · Programa referencial',
    '/images/showcase/creative-22-cover.png','/images/showcase/creative-22-cover.png',
    NULL,'/images/showcase/creative-22-stage.svg','Escena Lima · Demo',NULL,
    'CENTER','INFORMATION','STANDARD',1,1,
    1,'19:00 · Apertura de puertas y orientación\n19:30 · Inicio de la experiencia musical\n20:15 · Encuentro de voces\n21:00 · Bloque de bandas y escena\n22:00 · Cierre referencial',
    1,'General: acceso general al evento.\nPlatinium: acceso preferente y beneficios adicionales según las condiciones de esa entrada.\nLa ilustración de escenario no representa sectores ni butacas asignadas.',
    0,NULL,NULL,0,NULL,NULL,1,
    'Ilustración conceptual del escenario y el ambiente del público. No confirma accesos, sectores, asientos ni distribución real.',
    0,NULL,NOW()
FROM events WHERE id = 22 AND organizer_email = 'org@connectify.com'
ON DUPLICATE KEY UPDATE
    primary_color=VALUES(primary_color),accent_color=VALUES(accent_color),highlight_text=VALUES(highlight_text),
    cover_image_url=VALUES(cover_image_url),thumbnail_image_url=VALUES(thumbnail_image_url),
    background_image_url=VALUES(background_image_url),stage_image_url=VALUES(stage_image_url),
    organizer_brand_name=VALUES(organizer_brand_name),organizer_logo_url=VALUES(organizer_logo_url),
    cover_focus=VALUES(cover_focus),content_focus=VALUES(content_focus),info_position=VALUES(info_position),
    show_gallery=VALUES(show_gallery),show_organizer=VALUES(show_organizer),show_agenda=VALUES(show_agenda),
    agenda_text=VALUES(agenda_text),show_benefits=VALUES(show_benefits),benefits_text=VALUES(benefits_text),
    show_sponsors=VALUES(show_sponsors),sponsors_text=VALUES(sponsors_text),sponsor_logos_text=VALUES(sponsor_logos_text),
    show_media=VALUES(show_media),youtube_url=VALUES(youtube_url),spotify_url=VALUES(spotify_url),
    show_stage_map=VALUES(show_stage_map),stage_text=VALUES(stage_text),show_sessions=VALUES(show_sessions),
    session_text=VALUES(session_text),updated_at=VALUES(updated_at);

INSERT INTO event_presentation_settings
    (event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,
     background_image_url,stage_image_url,organizer_brand_name,cover_focus,content_focus,
     info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,
     benefits_text,show_sponsors,show_media,show_stage_map,stage_text,show_sessions,updated_at)
SELECT 8,'#0D2942','#36C6DD',
    'TECH SUMMIT · Ideas aplicadas, conversaciones reales · Demostración',
    '/images/showcase/creative-8-cover.png','/images/showcase/creative-8-cover.png',
    NULL,'/images/showcase/creative-8-stage.svg','Conexión Tech · Demo',
    'CENTER','INFORMATION','STANDARD',1,1,
    1,'19:00 · Acreditación y orientación\n19:30 · Apertura del encuentro\n20:00 · Panorama de inteligencia artificial aplicada\n20:45 · Conversación sobre nube y desarrollo web\n21:30 · Demostraciones y preguntas\n22:00 · Cierre referencial',
    1,'Acceso a las charlas y zona de demostración.\nEspacio de preguntas y conversación entre asistentes.\nAgenda y distribución de salas sujetas a confirmación; contenido demostrativo.',
    0,0,1,'Ilustración conceptual del escenario, el público y los temas de la conferencia. No representa butacas ni distribución real.',
    0,NOW()
FROM events WHERE id = 8 AND organizer_email = 'org@connectify.com'
ON DUPLICATE KEY UPDATE
    primary_color=VALUES(primary_color),accent_color=VALUES(accent_color),highlight_text=VALUES(highlight_text),
    cover_image_url=VALUES(cover_image_url),thumbnail_image_url=VALUES(thumbnail_image_url),
    background_image_url=VALUES(background_image_url),stage_image_url=VALUES(stage_image_url),
    organizer_brand_name=VALUES(organizer_brand_name),cover_focus=VALUES(cover_focus),
    content_focus=VALUES(content_focus),info_position=VALUES(info_position),
    show_gallery=VALUES(show_gallery),show_organizer=VALUES(show_organizer),show_agenda=VALUES(show_agenda),
    agenda_text=VALUES(agenda_text),show_benefits=VALUES(show_benefits),benefits_text=VALUES(benefits_text),
    show_sponsors=VALUES(show_sponsors),show_media=VALUES(show_media),show_stage_map=VALUES(show_stage_map),
    stage_text=VALUES(stage_text),show_sessions=VALUES(show_sessions),updated_at=VALUES(updated_at);

INSERT INTO event_presentation_settings
    (event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,
     background_image_url,stage_image_url,organizer_brand_name,cover_focus,content_focus,
     info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,
     benefits_text,show_sponsors,show_media,show_stage_map,stage_text,show_sessions,updated_at)
SELECT 9,'#6A3D26','#EA9C51',
    'SABORES DEL PERÚ · Cocina abierta y encuentro · Propuesta de demostración',
    '/images/showcase/creative-9-cover.png','/images/showcase/creative-9-cover.png',
    NULL,'/images/showcase/creative-9-stage.svg','Sabores del Perú · Demo',
    'CENTER','INFORMATION','STANDARD',1,1,
    1,'19:00 · Apertura de accesos\n19:30 · Inicio del recorrido gastronómico\n20:15 · Cocina abierta: ingredientes de temporada\n21:00 · Conversación sobre recetas y tradición\n21:45 · Último recorrido y cierre referencial',
    1,'General: acceso a stands, música en vivo y degustaciones según la entrada vigente.\nVIP: acceso preferente y cata guiada según la entrada vigente.\nOtros consumos o servicios requieren confirmación del organizador.',
    0,0,1,'Ilustración temática de cocina, stands, degustación y música. No confirma ubicación de puestos, sectores ni aforo.',
    0,NOW()
FROM events WHERE id = 9 AND organizer_email = 'org@connectify.com'
ON DUPLICATE KEY UPDATE
    primary_color=VALUES(primary_color),accent_color=VALUES(accent_color),highlight_text=VALUES(highlight_text),
    cover_image_url=VALUES(cover_image_url),thumbnail_image_url=VALUES(thumbnail_image_url),
    background_image_url=VALUES(background_image_url),stage_image_url=VALUES(stage_image_url),
    organizer_brand_name=VALUES(organizer_brand_name),cover_focus=VALUES(cover_focus),
    content_focus=VALUES(content_focus),info_position=VALUES(info_position),
    show_gallery=VALUES(show_gallery),show_organizer=VALUES(show_organizer),show_agenda=VALUES(show_agenda),
    agenda_text=VALUES(agenda_text),show_benefits=VALUES(show_benefits),benefits_text=VALUES(benefits_text),
    show_sponsors=VALUES(show_sponsors),show_media=VALUES(show_media),show_stage_map=VALUES(show_stage_map),
    stage_text=VALUES(stage_text),show_sessions=VALUES(show_sessions),updated_at=VALUES(updated_at);

INSERT INTO event_presentation_settings
    (event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,
     background_image_url,stage_image_url,organizer_brand_name,cover_focus,content_focus,
     info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,
     benefits_text,show_sponsors,show_media,show_stage_map,stage_text,show_sessions,updated_at)
SELECT 10,'#173A48','#B8D75A',
    'FINAL COPA INTERBARRIOS · La emoción se vive desde la tribuna · Demo',
    '/images/showcase/creative-10-cover.png','/images/showcase/creative-10-cover.png',
    NULL,'/images/showcase/creative-10-stage.svg','Copa Interbarrios · Demo',
    'CENTER','SALES','STANDARD',1,1,
    1,'18:30 · Acceso y orientación de tribunas\n19:30 · Inicio del partido\n20:15 · Descanso referencial\n21:30 · Cierre y salida ordenada',
    1,'Ambiente de partido, campo de juego y público en la tribuna.\nLas entradas digitales se validarán en el ingreso cuando se publiquen sus variantes.\nNo hay butacas ni sectores confirmados en esta demostración.',
    0,0,1,'Ilustración conceptual del campo y el ambiente de tribuna. Los accesos, sectores y lugares deben confirmarse por el organizador.',
    0,NOW()
FROM events WHERE id = 10 AND organizer_email = 'org@connectify.com'
ON DUPLICATE KEY UPDATE
    primary_color=VALUES(primary_color),accent_color=VALUES(accent_color),highlight_text=VALUES(highlight_text),
    cover_image_url=VALUES(cover_image_url),thumbnail_image_url=VALUES(thumbnail_image_url),
    background_image_url=VALUES(background_image_url),stage_image_url=VALUES(stage_image_url),
    organizer_brand_name=VALUES(organizer_brand_name),cover_focus=VALUES(cover_focus),
    content_focus=VALUES(content_focus),info_position=VALUES(info_position),
    show_gallery=VALUES(show_gallery),show_organizer=VALUES(show_organizer),show_agenda=VALUES(show_agenda),
    agenda_text=VALUES(agenda_text),show_benefits=VALUES(show_benefits),benefits_text=VALUES(benefits_text),
    show_sponsors=VALUES(show_sponsors),show_media=VALUES(show_media),show_stage_map=VALUES(show_stage_map),
    stage_text=VALUES(stage_text),show_sessions=VALUES(show_sessions),updated_at=VALUES(updated_at);

INSERT INTO event_presentation_settings
    (event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,
     background_image_url,stage_image_url,organizer_brand_name,cover_focus,content_focus,
     info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,
     benefits_text,show_sponsors,show_media,show_stage_map,stage_text,show_sessions,updated_at)
SELECT 12,'#122B39','#D6A86A',
    'IMPULSA NEGOCIOS · Conversaciones que abren posibilidades · Demo',
    '/images/showcase/creative-12-cover.png','/images/showcase/creative-12-cover.png',
    '/images/showcase/creative-12-cover.png','/images/showcase/creative-12-stage.svg',
    'Impulsa · Demo','CENTER','INFORMATION','STANDARD',1,1,
    1,'19:00 · Registro y bienvenida\n19:30 · Apertura de la conferencia\n20:00 · Conversación: de la idea a la ejecución\n20:45 · Panel de experiencias empresariales\n21:30 · Preguntas y encuentro entre asistentes\n22:00 · Cierre referencial',
    1,'Ingreso a la conferencia y a los espacios comunes.\nConversación abierta al final de la sesión.\nPonentes, contenidos y servicios adicionales son referenciales en esta demostración.',
    0,0,1,'Ilustración conceptual de escenario, ponencias y público. No indica butacas, sectores ni accesos definitivos.',
    0,NOW()
FROM events WHERE id = 12 AND organizer_email = 'org@connectify.com'
ON DUPLICATE KEY UPDATE
    primary_color=VALUES(primary_color),accent_color=VALUES(accent_color),highlight_text=VALUES(highlight_text),
    cover_image_url=VALUES(cover_image_url),thumbnail_image_url=VALUES(thumbnail_image_url),
    background_image_url=VALUES(background_image_url),stage_image_url=VALUES(stage_image_url),
    organizer_brand_name=VALUES(organizer_brand_name),cover_focus=VALUES(cover_focus),
    content_focus=VALUES(content_focus),info_position=VALUES(info_position),
    show_gallery=VALUES(show_gallery),show_organizer=VALUES(show_organizer),show_agenda=VALUES(show_agenda),
    agenda_text=VALUES(agenda_text),show_benefits=VALUES(show_benefits),benefits_text=VALUES(benefits_text),
    show_sponsors=VALUES(show_sponsors),show_media=VALUES(show_media),show_stage_map=VALUES(show_stage_map),
    stage_text=VALUES(stage_text),show_sessions=VALUES(show_sessions),updated_at=VALUES(updated_at);

COMMIT;

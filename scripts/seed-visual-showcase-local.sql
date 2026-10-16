-- Ejemplos locales voluntarios. No publicar ni ejecutar en producción.
-- Inserciones idempotentes: nunca reemplaza eventos existentes.
SET NAMES utf8mb4;
START TRANSACTION;
SET @showcase_owner = (SELECT email FROM user_accounts WHERE email='org@connectify.com' AND role='ORGANIZER' LIMIT 1);
INSERT INTO events(title,description,category_id,event_date,location,city,organizer_name,organizer_email,price,capacity,tickets_sold,image_url,featured,design_enabled,design_template,status,created_at,updated_at)
SELECT 'PULSO Lima · Festival de luz y sonido','Una noche de música electrónica, instalaciones de luz y arte escénico frente al Pacífico. Tres actos recorren el atardecer, la noche y el cierre inmersivo, con una zona gastronómica y espacios de descanso. Ingreso desde las 17:00; espectáculo de 18:00 a 00:30. Mayores de 18 años con documento de identidad. Acceso por zona según entrada, sin reingreso. Propuesta ficticia de demostración: no corresponde a una convocatoria real.',1,'2027-02-20 18:00:00','Recinto Costa Azul · espacio conceptual','Lima','Pulso Estudio',@showcase_owner,65,1040,0,'/images/showcase/pulso-lima-cover.png',0,0,'POSTER_LANDING','DRAFT',NOW(),NOW()
WHERE @showcase_owner IS NOT NULL AND NOT EXISTS (SELECT 1 FROM events WHERE title='PULSO Lima · Festival de luz y sonido' AND organizer_email=@showcase_owner);
SET @showcase_event=(SELECT id FROM events WHERE title='PULSO Lima · Festival de luz y sonido' AND organizer_email=@showcase_owner LIMIT 1);
INSERT INTO event_presentation_settings(event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,background_image_url,stage_image_url,organizer_brand_name,organizer_logo_url,sponsor_logos_text,cover_focus,content_focus,info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,benefits_text,show_sponsors,sponsors_text,show_media,youtube_url,spotify_url,show_stage_map,stage_text,show_sessions,session_text,updated_at)
SELECT @showcase_event,'#2925A9','#FF765E','SONIDO QUE SE VE. LUZ QUE SE SIENTE. · Edición conceptual 2027','/images/showcase/pulso-lima-cover.png','/images/showcase/pulso-lima-cover.png','/images/showcase/pulso-lima-cover.png','/images/showcase/pulso-lima-stage.svg','Pulso Estudio','/images/showcase/organizer-mark.svg','/images/showcase/partner-onda.svg
/images/showcase/partner-raiz.svg
/images/showcase/partner-nodo.svg','CENTER','BALANCED','FLOATING',1,1,1,'17:00 · Apertura, control de acceso y arte lumínico
18:00 · Sesión Horizonte: electrónica orgánica
20:00 · Acto Pulso: performance audiovisual
22:00 · Sesión Océano: escenario principal
00:00 · Cierre de luz · salida escalonada hasta las 00:30',1,'Todas las zonas: hidratación, baños, primeros auxilios y ruta accesible.
VIP y Platinum: acceso preferente y lounge exclusivo.
Palco: vista elevada con asiento individual.
Meet & Greet: encuentro de 20 minutos después del primer acto.
No incluye transporte ni consumo adicional. QR personal y documento para ingreso.',1,'Aliados ficticios de esta demostración: ONDA · RAÍZ · NODO. No representan patrocinios reales.',1,'https://www.youtube.com/watch?v=aqz-KE-bpKQ','https://open.spotify.com/playlist/37i9dQZF1DX4sWSpwq3LiO',1,'Escenario al norte. Platinum frente al escenario; VIP a ambos lados; Preferencial al centro; General al sur; Palco lateral elevado; Meet & Greet junto al backstage. Ruta accesible por el lateral este. Cupos: 600 + 240 + 100 + 60 + 24 + 16 = 1,040. Plano ilustrativo, sin asignación numerada de asientos.
Media de referencia: cortometraje Big Buck Bunny y playlist Peaceful Piano; no son material promocional de este evento.',1,'20 febrero 2027 · Lima · Apertura 17:00 / show 18:00
20 febrero 2027 · Encuentro Meet & Greet · 19:15 (solo variante correspondiente)
21 febrero 2027 · Cierre y salida · 00:30',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_presentation_settings WHERE event_id=@showcase_event);
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'General',65,600,0,1,'Acceso al campo General, instalaciones de luz y zona gastronómica. Público de pie.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='General');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Preferencial',95,240,0,1,'Sector central más cercano al escenario. Acceso a instalaciones y servicios generales.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Preferencial');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'VIP',150,100,0,1,'Área lateral VIP, ingreso rápido y lounge de descanso. Sin bebidas incluidas.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='VIP');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Platinum',220,60,0,1,'Franja frontal exclusiva, ingreso preferente y lounge Platinum. Público de pie.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Platinum');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Palco',280,24,0,1,'Asiento individual en plataforma lateral elevada, con visibilidad panorámica.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Palco');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Meet & Greet',340,16,0,1,'Acceso Platinum y encuentro programado de 20 minutos. Ingreso con identificación.',NOW(),'2027-02-20 18:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Meet & Greet');
INSERT INTO event_admin_records(event_id,type,description,created_at)
SELECT @showcase_event,'CREATED','Ejemplo visual de demostración creado localmente, con seis variantes y plano ilustrativo. No publicado.',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_admin_records WHERE event_id=@showcase_event AND type='CREATED');

INSERT INTO events(title,description,category_id,event_date,location,city,organizer_name,organizer_email,price,capacity,tickets_sold,image_url,featured,design_enabled,design_template,status,created_at,updated_at)
SELECT 'ORIGEN · Mesa del Pacífico','Una experiencia gastronómica que conecta huerto, costa y mesa. Estaciones de cocina abierta, mercado de productores y una sobremesa musical reúnen ingredientes de temporada y técnicas contemporáneas. Apertura a las 11:30, actividades de 12:00 a 20:00. Las degustaciones incluidas dependen del pase; opciones vegetarianas previa coordinación con soporte. Menores acompañados por un adulto. Propuesta ficticia de demostración: precios, recinto y programa son ilustrativos.',3,'2027-03-13 12:00:00','Patio Mar de Tierra · espacio conceptual','Lima','Origen Taller',@showcase_owner,35,552,0,'/images/showcase/origen-cover.png',0,0,'IMMERSIVE','DRAFT',NOW(),NOW()
WHERE @showcase_owner IS NOT NULL AND NOT EXISTS (SELECT 1 FROM events WHERE title='ORIGEN · Mesa del Pacífico' AND organizer_email=@showcase_owner);
SET @showcase_event=(SELECT id FROM events WHERE title='ORIGEN · Mesa del Pacífico' AND organizer_email=@showcase_owner LIMIT 1);
INSERT INTO event_presentation_settings(event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,background_image_url,stage_image_url,organizer_brand_name,organizer_logo_url,sponsor_logos_text,cover_focus,content_focus,info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,benefits_text,show_sponsors,sponsors_text,show_media,youtube_url,spotify_url,show_stage_map,stage_text,show_sessions,session_text,updated_at)
SELECT @showcase_event,'#38523F','#D49A60','DEL TERRITORIO A LA MESA · Cocina abierta, oficios y encuentro','/images/showcase/origen-cover.png','/images/showcase/origen-cover.png','/images/showcase/origen-cover.png','/images/showcase/origen-stage.svg','Origen Taller','/images/showcase/organizer-mark.svg','/images/showcase/partner-onda.svg
/images/showcase/partner-raiz.svg
/images/showcase/partner-nodo.svg','CENTER','INFORMATION','STANDARD',1,1,1,'11:30 · Bienvenida y recorrido de productores
12:00 · Apertura del mercado y cocina de costa
13:30 · Taller de ingredientes de temporada
15:00 · Mesa de autor: servicio guiado
17:00 · Conversación de productores y técnicas
18:30 · Sobremesa acústica · cierre 20:00',1,'Acceso al mercado, demostraciones abiertas y música acústica con todos los pases.
Preferencial: dos degustaciones; VIP: cuatro degustaciones y taller.
Platinum: menú guiado de cinco pasos con bebida sin alcohol.
Palco: mesa individual junto al escenario de cocina.
Meet & Greet: conversación guiada con el equipo de cocina.
Informar alergias; no se garantiza ausencia de trazas.',1,'Aliados ficticios de esta demostración: ONDA · RAÍZ · NODO. No representan patrocinios reales.',1,'https://www.youtube.com/watch?v=aqz-KE-bpKQ','https://open.spotify.com/playlist/37i9dQZF1DX4sWSpwq3LiO',1,'Cocina abierta al norte; Platinum frente al pase; VIP al este; Preferencial junto al huerto; General en mercado central; Palco con mesas junto a cocina; Meet & Greet en Taller del Chef. 300 + 120 + 60 + 36 + 24 + 12 = 552 plazas. Circulación accesible, agua, baños y asistencia señalizados. Plano conceptual.
Media de referencia: cortometraje Big Buck Bunny y playlist Peaceful Piano; no son material promocional de este evento.',1,'13 marzo 2027 · Mercado y cocina abierta · 12:00–20:00
13 marzo 2027 · Mesa de autor · 15:00 (Platinum)
13 marzo 2027 · Taller del Chef · 16:30 (Meet & Greet)',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_presentation_settings WHERE event_id=@showcase_event);
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'General',35,300,0,1,'Recorrido del mercado, cocina en vivo y sobremesa musical. Consumos no incluidos.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='General');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Preferencial',65,120,0,1,'Acceso General más dos degustaciones de estaciones seleccionadas.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Preferencial');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'VIP',115,60,0,1,'Cuatro degustaciones, taller de ingredientes y acceso preferente.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='VIP');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Platinum',185,36,0,1,'Menú de cinco pasos a las 15:00 y una bebida sin alcohol.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Platinum');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Palco',230,24,0,1,'Plaza individual en mesa junto a cocina, menú de cinco pasos y vista al pase.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Palco');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Meet & Greet',275,12,0,1,'Experiencia Platinum y conversación guiada de 30 minutos con el equipo de cocina.',NOW(),'2027-03-13 12:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Meet & Greet');
INSERT INTO event_admin_records(event_id,type,description,created_at)
SELECT @showcase_event,'CREATED','Ejemplo visual de demostración creado localmente, con seis variantes y plano ilustrativo. No publicado.',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_admin_records WHERE event_id=@showcase_event AND type='CREATED');

INSERT INTO events(title,description,category_id,event_date,location,city,organizer_name,organizer_email,price,capacity,tickets_sold,image_url,featured,design_enabled,design_template,status,created_at,updated_at)
SELECT 'NEXUM · Ideas que conectan','Un foro de innovación aplicada para profesionales, estudiantes y equipos. Conferencias, laboratorios de prototipado y espacios de conversación conectan diseño, tecnología y negocio. Acreditación desde las 08:00; programa principal de 09:00 a 18:30. Los laboratorios requieren el pase indicado y tienen aforo definido. Certificado digital de participación, sin acreditación académica. Propuesta ficticia de demostración: ponencias, sede y precios ilustrativos.',2,'2027-04-09 09:00:00','Foro Horizonte · espacio conceptual','Lima','Nexum Lab',@showcase_owner,45,590,0,'/images/showcase/nexum-cover.png',0,0,'TICKET_PANEL','DRAFT',NOW(),NOW()
WHERE @showcase_owner IS NOT NULL AND NOT EXISTS (SELECT 1 FROM events WHERE title='NEXUM · Ideas que conectan' AND organizer_email=@showcase_owner);
SET @showcase_event=(SELECT id FROM events WHERE title='NEXUM · Ideas que conectan' AND organizer_email=@showcase_owner LIMIT 1);
INSERT INTO event_presentation_settings(event_id,primary_color,accent_color,highlight_text,cover_image_url,thumbnail_image_url,background_image_url,stage_image_url,organizer_brand_name,organizer_logo_url,sponsor_logos_text,cover_focus,content_focus,info_position,show_gallery,show_organizer,show_agenda,agenda_text,show_benefits,benefits_text,show_sponsors,sponsors_text,show_media,youtube_url,spotify_url,show_stage_map,stage_text,show_sessions,session_text,updated_at)
SELECT @showcase_event,'#123A4A','#46C4BD','IDEAS CLARAS. CONEXIONES REALES. · Foro de innovación aplicada','/images/showcase/nexum-cover.png','/images/showcase/nexum-cover.png','/images/showcase/nexum-cover.png','/images/showcase/nexum-stage.svg','Nexum Lab','/images/showcase/organizer-mark.svg','/images/showcase/partner-onda.svg
/images/showcase/partner-raiz.svg
/images/showcase/partner-nodo.svg','CENTER','BALANCED','SIDEBAR',1,1,1,'08:00 · Acreditación y café de bienvenida
09:00 · Apertura: diseñar con intención
10:30 · Panel: tecnología aplicada a servicios
12:00 · Laboratorios de prototipado por nivel de pase
14:30 · Casos y demostraciones en escenario principal
16:00 · Rondas de conversación
18:00 · Cierre y conclusiones · salida 18:30',1,'Conferencias, expo de proyectos y certificado digital con todos los pases.
Estudiante: mismo acceso General con identificación vigente.
Preferencial: primeras filas centrales y material del foro.
VIP: laboratorio de prototipado y lounge.
Corporativo: pase individual para equipos, laboratorio y mesa de trabajo.
Meet & Greet: sesión de preguntas con facilitadores al final del foro.',1,'Aliados ficticios de esta demostración: ONDA · RAÍZ · NODO. No representan patrocinios reales.',1,'https://www.youtube.com/watch?v=aqz-KE-bpKQ','https://open.spotify.com/playlist/37i9dQZF1DX4sWSpwq3LiO',1,'Auditorio principal al norte; General al centro y atrás; Estudiante al oeste; Preferencial en filas centrales delanteras; VIP al este; Corporativo en laboratorio B; Meet & Greet en sala de encuentro. 260 + 100 + 100 + 50 + 60 + 20 = 590 plazas. Ruta accesible sin escalones, acreditación y salidas señalizadas. Sectores conceptuales sin butaca asignada.
Media de referencia: cortometraje Big Buck Bunny y playlist Peaceful Piano; no son material promocional de este evento.',1,'09 abril 2027 · Auditorio principal · 09:00–18:30
09 abril 2027 · Laboratorio A · 12:00 (VIP y Meet & Greet)
09 abril 2027 · Laboratorio B · 12:00 (Corporativo)',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_presentation_settings WHERE event_id=@showcase_event);
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'General',90,260,0,1,'Conferencias, expo de proyectos y certificado digital de participación.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='General');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Estudiante',45,100,0,1,'Acceso General presentando identificación estudiantil vigente.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Estudiante');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Preferencial',130,100,0,1,'Primeras filas centrales y material digital del foro. No incluye laboratorio.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Preferencial');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'VIP',210,50,0,1,'Acceso Preferencial, laboratorio A y lounge de conversación.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='VIP');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Corporativo',250,60,0,1,'Pase individual de equipo: laboratorio B, material y mesa de trabajo.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Corporativo');
INSERT INTO ticket_types(event_id,name,price,quantity_available,quantity_sold,active,description,sales_start,sales_end)
SELECT @showcase_event,'Meet & Greet',320,20,0,1,'Acceso VIP y ronda de preguntas de 30 minutos con facilitadores.',NOW(),'2027-04-09 09:00:00' WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ticket_types WHERE event_id=@showcase_event AND name='Meet & Greet');
INSERT INTO event_admin_records(event_id,type,description,created_at)
SELECT @showcase_event,'CREATED','Ejemplo visual de demostración creado localmente, con seis variantes y plano ilustrativo. No publicado.',NOW() WHERE @showcase_event IS NOT NULL AND NOT EXISTS(SELECT 1 FROM event_admin_records WHERE event_id=@showcase_event AND type='CREATED');

COMMIT;
SELECT id,title,status,capacity FROM events WHERE title IN ('PULSO Lima · Festival de luz y sonido','ORIGEN · Mesa del Pacífico','NEXUM · Ideas que conectan') AND organizer_email=@showcase_owner;



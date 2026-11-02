# Espíritu del Perú

Arte y presentación del Marketplace, únicamente en `/events`.

- Etiqueta en la barra principal: «Espíritu del Perú», con miniatura animada y control para repetir/detener.
- Barra principal con el vidrio semitransparente habitual (fondo blanco al 50%). Se retiró la muestra de bandera del fondo por petición del usuario.
- Entrada de 6 segundos: acercamiento del arte, ondulación localizada de la bandera, luz solar, estrellas y aparición del nombre. Al terminar vuelve la fotografía habitual del Hero.
- Brillo y estrella de la etiqueta «Marketplace de eventos»: ciclo de 12 segundos.
- No modifica tamaños del Hero, filtros, tarjetas ni navegación. Visitas al Perú conserva su panel.
- Sin audio ni reproductor externo. Es una ilustración animada mediante SVG/CSS/JavaScript, no un vídeo generado de personajes articulados.
- Respeta movimiento reducido: no se inicia automáticamente ni se repiten destellos. La reproducción manual muestra la imagen quieta durante 6 segundos.
- Ocultar la pestaña o salir de la página detiene la secuencia. Pulsar durante la animación la detiene.

## Biblioteca

`src/main/resources/static/images/peru/` contiene el JPG optimizado y cinco referencias aportadas por el usuario, conservadas sin alteración. La biblioteca privada de administrador/desarrollador las descubre como Decoraciones; se añadió lectura de AVIF al catálogo. La web usa solo el arte original generado, no las referencias fotográficas. No hay cambios de tablas ni almacenamiento en Supabase.

Generado con la herramienta integrada de imágenes, original PNG conservado en el directorio de imágenes generadas de Codex; JPG del proyecto de 1536 × 1024. Referencia de YouTube aportada: https://youtu.be/CXpVcb8Y11U (no accesible durante la implementación; no se descargó ni incorporó su vídeo).

Prompt utilizado: “Create a premium cinematic anime opening key visual for a Peruvian events website. Wide landscape 1536x1024 artwork (main composition concentrated in central horizontal band, usable when cropped to wide 2:1). Original heroic adult Inka ruler, strong indigenous Andean features, long black hair, intricate gold royal Inca headband and sun medallion, red woven cape, gold and turquoise tunic, expressive determined face, waist-up heroic three-quarter pose at RIGHT quarter looking left toward sunrise, cape billowing. Behind him unmistakable Machu Picchu stone terraces and Huayna Picchu mountains with atmospheric teal mist. At LEFT quarter a beautiful waving plain red-white-red Peruvian flag, a brilliant warm sun above mountain horizon. Golden dust and a few star highlights. Sophisticated hand-painted anime / action animation key art with crisp illustrated edges, dramatic cinematic light, spectacular but dignified, blue teal midnight and antique gold palette. Keep actor face clearly readable even in miniature. No words, no letters, no UI, no logos, no watermark, no modern buildings, no copying existing franchise characters. This is original illustration for a 6-second animated motion composition; leave some breathing room around figure.”

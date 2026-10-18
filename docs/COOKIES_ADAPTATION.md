# Adaptación de la política de cookies

Fecha: 18 de octubre de 2026. Alcance: `/cookies` y texto del aviso informativo compartido. No incluye publicación ni un gestor de consentimiento de terceros.

## Referencia de Figma

Se leyó «Política de Cookies de CONNECTIFY SOLUTIONS» en la vista previa del [modelo Figma Make](https://www.figma.com/make/3M767E0NzAxGiAkA2SzR0X/Connectify-Eventos--26-05---Copia-Del722-?code-node-id=0-9&p=f&fullscreen=1), con fecha noviembre de 2024.

Ese texto utiliza un RUC y domicilio distintos de los demás documentos del modelo. Se utilizaron los datos legales confirmados por el titular durante la adaptación de términos y privacidad: CONNECTIFY SOLUTIONS S.A.C., RUC 10068064631 y domicilio en César Canevaro, Lince. No se trasladó la identidad divergente del documento de cookies.

Se retiraron afirmaciones no respaldadas sobre geolocalización, predicción de preferencias y campañas de marketing como funcionalidades propias. Tampoco se copiaron las afirmaciones contradictorias sobre anonimato, daños al dispositivo o posibilidad de prescindir de cookies y mantener cualquier función de acceso.

## Inventario contrastado con el proyecto

| Elemento | Evidencia | Persistencia |
| --- | --- | --- |
| `JSESSIONID` | Cabecera de respuesta de `/login`, consultada sin guardar ni mostrar el valor del identificador. Cookie HttpOnly, sin Expires/Max-Age en la respuesta local. | Sesión; también depende de la caducidad del servidor y restauración del navegador. |
| `connectify.cookies.accepted` | `fragments/site-footer.html` | localStorage, sin caducidad definida por la aplicación. |
| `connectify.launch.2026-10-14.seen` | `static/js/launch-welcome.js` | sessionStorage. |
| `connectify.izipay.pendingOrder` | `static/js/checkout-izipay.js` | sessionStorage; el checkout retira referencias en determinados estados. |

El perfil de producción configura la cookie de sesión con Secure, HttpOnly y SameSite=Lax. Esta tarea no modifica esas opciones ni inicia una sesión autenticada para auditar pagos.

El botón del aviso se renombró de «Aceptar» a «Entendido», con una explicación de su alcance. Se conservan el identificador, clave de almacenamiento y comportamiento del aviso; no se atribuye consentimiento publicitario al valor histórico `accepted`. No se cambiaron la composición del footer, cards ni menús.

## Servicios externos y límites de la revisión

Se identificaron el script del checkout de IZIPay, iframes de YouTube y Spotify en `events/marketplace-purchase.html`, e imágenes de QR desde `api.qrserver.com` en confirmación y tickets. Las imágenes de eventos también pueden proceder de URLs aportadas por organizadores. No se afirma que todos esos recursos instalen cookies; sí contactan dominios externos al cargarse.

No se encontraron etiquetas propias de Google Analytics, Meta Pixel, Hotjar o Clarity en los archivos revisados. Esto no equivale a una auditoría exhaustiva de los scripts de terceros en producción.

**Pendiente funcional:** los iframes actuales tienen `src` y carga diferida, pero no bloqueo previo por consentimiento. El aviso compartido no es un gestor de categorías y no los bloquea. Debe revisarse y habilitarse un control previo para tecnologías opcionales que requieran consentimiento, con rechazo y revocación efectivos. La política informa del estado actual y no declara resuelto ese pendiente. El cambio del texto no convierte una carga automática en consentida.

**Pendiente de privacidad:** completar el inventario de destinatarios y recursos externos en la revisión de privacidad; revisar en particular la generación externa de QR que comunica el código del ticket al proveedor y la alternativa de generación dentro de la aplicación. No se transmitieron tickets ni se hicieron compras durante esta tarea.

## Referencias

- [ANPD, OC 02-2022, información y tratamiento a través de cookies](https://www.gob.pe/institucion/anpd/informes-publicaciones/2670212-oc-n-02-2022-jus-dgtaipd-sobre-disposiciones-de-la-ley-n-29733-ley-de-proteccion-de-datos-personales-y-su-reglamento-derecho-de-informacion-transferencia-de-datos-y-tratamiento-de-datos-a-traves-de-cookies).
- [ANPD, resolución 2039-2022, distinción entre cookies necesarias y marketing de terceros](https://cdn.www.gob.pe/uploads/document/file/5354960/4795140-rd-2039-2022.pdf).
- [Reglamento vigente, D. S. 016-2024-JUS](https://www.gob.pe/institucion/anpd/normas-legales/6554453-n-016-2024-jus).
- [MDN, cookies HTTP](https://developer.mozilla.org/es/docs/Web/HTTP/Guides/Cookies).
- [MDN, Web Storage](https://developer.mozilla.org/es/docs/Web/API/Web_Storage_API).
- [Google, cookies y tecnologías similares](https://policies.google.com/technologies/cookies?hl=es).
- [Spotify, política de cookies](https://www.spotify.com/legal/cookies-policy/).

## Verificación local

`/cookies` responde HTTP 200 y muestra las siete secciones, el inventario de cuatro elementos y las dos hojas de estilos legales. Se comprobaron los destinos del índice y la apertura del inventario por su enlace. La ventana de 1280 × 720 no presenta desbordamiento horizontal de la página. En una visita local con origen separado y sin preferencia previa se comprobó que «Entendido» oculta el aviso y que permanece oculto tras recargar. La prueba no acepta términos contractuales ni modifica compras o cuentas. `git diff --check` sin incidencias.

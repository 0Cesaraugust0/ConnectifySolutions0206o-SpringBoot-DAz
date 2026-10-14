# IZIPay Web Core — Pop-In
El checkout Java/Spring Boot usa el SDK oficial Web Core en modalidad `pop-up`.
Carrito → checkout → **Proceder al pago** abre el formulario de IZIPay.
La integración anterior Mi Cuenta Web/Krypton conserva sus clases por compatibilidad,
pero sus rutas de confirmación están bloqueadas cuando Web Core está habilitado.

## Configuración privada
Copiar `src/main/resources/application-local.properties.example` a
`application-local.properties` en la raíz del proyecto.
Completar las cuatro credenciales del **Panel de Comercio sandbox**:
código de comercio, clave API del nuevo botón de pagos, clave pública RSA y clave Hash.
No subir el archivo real a Git. No usar credenciales Krypton en estos campos.

Variables equivalentes:
- `IZIPAY_WEB_ENABLED=true`
- `IZIPAY_WEB_ENVIRONMENT=sandbox` (producción solo después de homologar: `production`)
- `IZIPAY_WEB_MERCHANT_CODE`
- `IZIPAY_WEB_API_KEY`
- `IZIPAY_WEB_PUBLIC_KEY`
- `IZIPAY_WEB_HASH_KEY`
- `IZIPAY_WEB_PAYMENT_METHODS=CARD`
- `IZIPAY_WEB_IPN_URL`

Sin credenciales completas, el checkout muestra pagos no disponibles y no genera tickets.
No hay credenciales de comercio incluidas ni pagos reales realizados por las pruebas.

## Tarjetas, Yape y Plin
Se inicia con `CARD`. Una vez habilitados por IZIPay, configurar
`CARD,YAPE_CODE,PAGO_PUSH` para tarjetas, código Yape y Plin Interbank.
La disponibilidad final depende de la afiliación y los límites del comercio.
El SDK solicita los datos sensibles; Connectify no recibe números de tarjeta ni CVV.
Los datos de facturación se completan por el cliente; no se inventa una dirección.

## Confirmación de pagos
- El servidor calcula el monto desde precios e inventario y guarda un snapshot de compra
  en `izipay_web_attempts`.
- El token de IZIPay se obtiene exclusivamente en backend y sólo autoriza esa orden.
- La respuesta se valida con HMAC-SHA256/Base64 sobre el `payloadHttp` literal.
- Se cotejan comercio, orden, transacción, importe, moneda y autorización.
- Los callbacks duplicados no duplican compras ni tickets.
- La notificación pública es `POST /payments/izipay/web/notification`.
  Requiere la firma de IZIPay; no necesita la cookie del comprador.
- La consulta del estado exige la cuenta propietaria de la compra.
- El inicio exige un token de sesión adicional para proteger el formulario.
- Las respuestas del API de checkout usan `Cache-Control: no-store`.

Para recibir IPN, configurar la URL HTTPS pública de este endpoint en
`izipay.web.ipn-url` y habilitar/configurar las notificaciones en el comercio según IZIPay.
localhost no es accesible para IZIPay. Sin IPN, una pérdida del callback del navegador
puede dejar el pago pendiente hasta una conciliación manual.

## Estados y límites operativos
`PENDING`: esperando confirmación; `COMPLETED`: compra y tickets emitidos;
`FAILED`: rechazo definitivo firmado; `REVIEW`: pago autorizado con inventario cambiado.

Un cierre o un error de comunicación no demuestran que no hubo cobro.
Las órdenes pendientes vencidas bloquean nuevos cobros hasta conciliar con IZIPay.
El token vence en aproximadamente 15 minutos y es de un solo uso.
La aplicación no incluye aún consulta automática de conciliación, reembolso ni panel
administrativo para resolver órdenes pendientes/en revisión.

No hay reserva temporal de inventario mientras el usuario paga.
El inventario se revalida con bloqueo al emitir los tickets. Si cambió, se conserva
la orden pagada en `REVIEW` y se debe resolver o reembolsar desde soporte del comercio.
Verificar este procedimiento antes de habilitar producción.

En desarrollo, Hibernate crea `izipay_web_attempts` con `ddl-auto=update`.
En producción (`ddl-auto=validate`) debe prepararse y revisarse la migración de esta
tabla e índices antes de desplegar esta versión.

## Verificación
Ejecutar `mvn test` con JDK 21. Las pruebas automatizadas usan datos ficticios,
sin contacto con IZIPay: firma alterada, importe/orden incorrectos, repetición
de callback, privacidad de la cuenta y protección del inicio.
Para validar el SDK real se necesita el comercio sandbox y sus credenciales,
además de probar aprobación, rechazo, cierre y notificación retrasada.

## Documentación oficial
- https://developers.izipay.pe/web-core/quickstart/
- https://developers.izipay.pe/web-core/modalidades/popup/
- https://developers.izipay.pe/web-core/modalidades/firma-validation/
- https://developers.izipay.pe/credentials/
- https://developers.izipay.pe/api/#/operations/generate_token
- https://developers.izipay.pe/web-core/use-cases/pay_with_yape_code/
- https://developers.izipay.pe/web-core/use-cases/pay_with_plin_interbank/

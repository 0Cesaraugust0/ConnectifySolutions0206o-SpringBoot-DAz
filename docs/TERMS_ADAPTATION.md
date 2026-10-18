# Adaptación de términos del marketplace

Fecha: 18 de octubre de 2026. Alcance: página pública `/terms` del proyecto Java/Spring Boot. Cambios locales; este trabajo no realiza un despliegue.

## Modelo consultado y datos conservados

Se leyó el documento «Términos y Condiciones de Uso», actualizado en noviembre de 2024, desde la vista previa del [modelo Figma Make](https://www.figma.com/make/3M767E0NzAxGiAkA2SzR0X/Connectify-Eventos--26-05---Copia-Del722-?t=xW3R5xPpjdorS2Ga-0).

El titular confirmó conservar CONNECTIFY SOLUTIONS S.A.C., RUC 10068064631 y contacto@connectify.com. El domicilio y WhatsApp se trasladaron del modelo. Esto documenta los datos proporcionados, no una verificación registral independiente. El dominio del servicio se actualizó a www.showyouplay.com; el correo confirmado conserva connectify.com.

## Adaptaciones al funcionamiento real

- Se ordenó la numeración del modelo (que saltaba de 8 a 14 y 24) y se mantuvieron los anclajes anteriores de servicio, roles, compras, organizador, acceso y responsabilidad.
- Se delimitó el servicio a eventos y entradas; se retiraron las referencias a préstamos, intercambios y productos que no corresponden al marketplace actual.
- Se describieron cuentas Cliente/Organizador y los permisos internos sin presentar los roles administrativos como registro público.
- Se explicó que conservar un carrito no reserva entradas ni congela precios.
- Se reflejó el cargo actual de servicio del 5 % y la moneda PEN del checkout.
- Se describió IZIPay y los medios de pago únicamente cuando estén habilitados. Cerrar el formulario no confirma ni anula por sí solo un pago; se requiere confirmación validada para emitir tickets. Se retiró la promesa genérica de procesamiento en 48 horas.
- Se incorporó la consulta de tickets, validación individual del QR y límites de las imágenes del escenario respecto de la asignación de asientos.
- Se sustituyó la prohibición absoluta de devoluciones por los derechos aplicables y se explicó la regla especial de la Ley 32415 dentro de su ámbito. No se declaró no reembolsable el cargo de servicio ni se prometió un reembolso automático no implementado.
- Se retiró la exoneración general de responsabilidad propia y la sumisión exclusiva a tribunales de Lima.
- Se distinguieron comunicaciones operativas y consentimiento publicitario. Se reconoció la titularidad de contenidos del Organizador y terceros.
- Se conservó el diseño compacto de la página con estilos exclusivos para el índice, datos del titular y notas de pago/devolución.

## Referencias oficiales consultadas

- [Código de Protección y Defensa del Consumidor, Ley 29571](https://www.gob.pe/institucion/indecopi/normas-legales/1244218-29571).
- [Ley 32415, texto consolidado](https://leyes.congreso.gob.pe/Documentos/2021_2026/ADLP/Texto_Consolidado/32415-TXM.pdf), artículos 2, 3 y 7. Comprende conciertos con aforo de al menos 500 personas, con exclusiones expresas. Devolución total de entrada por cancelación o cambios de lugar, fecha u objeto no aceptados; máximo 15 días hábiles desde la solicitud. No se generalizó este ámbito a todos los eventos.
- [Indecopi: Libro de Reclamaciones](https://consumidor.gob.pe/libro-de-reclamaciones/). Se diferenció el plazo de respuesta al reclamo del plazo de devolución.
- [Reglamento de la Ley de Protección de Datos Personales, D. S. 016-2024-JUS](https://www.gob.pe/institucion/anpd/normas-legales/6554453-n-016-2024-jus).

## Pendientes funcionales identificados

La adaptación del texto no implementa estas funciones ni certifica que el sitio cumpla todas las obligaciones legales:

1. **Libro de Reclamaciones virtual:** no se encontró una página o flujo dedicado. Ayuda y el buzón de soporte no lo sustituyen. Debe implementarse el acceso visible, registro y atención formal correspondientes.
2. **Devoluciones:** no se encontró un flujo automatizado de reembolso. El texto utiliza los canales existentes para solicitar atención y no promete botones o reintegros automáticos inexistentes. Falta definir y operar el procedimiento de cada caso y proveedor.
3. **Privacidad:** adaptada posteriormente desde el modelo de Figma el 18 de octubre de 2026. Ver `docs/PRIVACY_ADAPTATION.md` para la comparación y los datos pendientes de confirmar antes de publicarla.
4. **Datos del evento y ticket:** comprobar la identificación del Organizador y demás información exigible en todas las variantes, en especial para conciertos sujetos a la Ley 32415.
5. **Aceptación versionada:** esta tarea actualiza el documento público, no añade un historial de aceptación ni cambia los formularios de registro o checkout.

## Verificación local

Se comprobó la respuesta HTTP 200 de `/terms`, el renderizado de Thymeleaf, la carga de la hoja de estilos, las 16 secciones y sus enlaces del índice. En una ventana de 1280 × 720 no se detectó desbordamiento horizontal. No se modificó código de pagos, datos de usuarios ni esquema de base de datos.

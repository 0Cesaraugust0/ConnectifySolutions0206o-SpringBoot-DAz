# Adaptación de la política de privacidad

Fecha: 18 de octubre de 2026. Página local: `/privacy`. No incluye publicación, cambios de infraestructura, acceso a registros de clientes ni modificación del tratamiento efectivo de datos.

## Modelo y adaptación

Se leyó la política completa en la vista previa del [modelo de Figma Make](https://www.figma.com/make/3M767E0NzAxGiAkA2SzR0X/Connectify-Eventos--26-05---Copia-Del722-?code-node-id=0-9&p=f&fullscreen=1), actualizada en noviembre de 2024.

Se conservaron el responsable, RUC, domicilio y correo del modelo, de acuerdo con la confirmación previa del titular sobre sus datos legales. El dominio es ahora www.showyouplay.com. La identidad declarada no se verificó ante registros públicos.

Se sustituyó la referencia al reglamento derogado D. S. 003-2013-JUS por el D. S. 016-2024-JUS. Se corrigieron la exclusión errónea de datos obtenidos de fuentes públicas, el consentimiento general por uso del sitio y la atribución de todo riesgo al usuario.

Las categorías reflejan `UserAccount`, `ShoppingCart`, `Purchase`, `Ticket`, `IzipayWebAttempt`, mensajes y auditorías. El checkout conserva el objeto de facturación, incluidos dirección y documento, además de los intentos de pago. No se afirmó que solo se almacena un resultado de pago. La contraseña se describe como resumen no reversible, no como cifrado reversible.

Se separaron finalidades operativas y publicidad opcional; se describieron los permisos por rol sin prometer confidencialidad absoluta. Los carritos y operaciones incompletas no se eliminan al cerrar la ventana de pago. Se incluyeron criterios de conservación, solicitudes ARCO, portabilidad cuando corresponda y medios proporcionados para verificar identidad.

## Proveedores comprobados

- Hostinger: servidor de la aplicación en Boston, Estados Unidos, según la ubicación confirmada por el titular el 18 de octubre de 2026. Esta ubicación se incorpora como dato proporcionado por el titular; no se deduce de la IP o del dominio ni se presenta como verificación independiente del centro de datos.
- Supabase: base de datos de producción según `docs/supabase-deployment.md`. Se consultó exclusivamente la metadata del proyecto, sin tablas ni datos de usuarios, el 18 de octubre de 2026. Su región principal es `ca-central-1`, correspondiente a Canadá Central según la documentación oficial. No se afirma que todas las copias, registros o subencargados estén exclusivamente en ese país.
- IZIPay: formulario y procesamiento de pago, según la integración del proyecto.

No se trasladaron los proveedores Google Cloud Iowa ni Amazon EC2 Ohio/Virginia del modelo, al no corresponder al despliegue documentado.

## Información pendiente antes de publicar

1. **Banco de datos:** el modelo afirma la inscripción del «Banco de Datos Personales de Usuarios Web», pero no se dispone de constancia, denominación oficial ni número de registro. La página no afirma que la inscripción esté acreditada. Confirmar y completar esos datos y las obligaciones registrales aplicables.
2. **Encargados y transferencias:** el titular ya confirmó el país del VPS (Estados Unidos). Queda completar la identidad contractual de los proveedores, sus subencargados y garantías efectivas para encargos y transferencias. La región primaria de Supabase y la ubicación del VPS no acreditan por sí solas el cumplimiento legal.
3. **Conservación:** aprobar plazos concretos por categoría y su procedimiento de eliminación o bloqueo. La página expone criterios y no inventa un borrado automático inexistente.
4. **Canal de privacidad:** comprobar operativamente que el correo confirmado recibe y permite responder solicitudes. No se envió correo de prueba.
5. **Consentimientos:** la adaptación de texto no añade casillas ni registro de consentimiento publicitario o de transferencias. Estos deben implementarse antes de activar tratamientos que los requieran.
6. **Libro de Reclamaciones:** sigue pendiente de implementación; no se copió el enlace inexistente del modelo ni se afirma que el buzón lo sustituye.

## Fuentes oficiales

- [Ley 29733](https://www.gob.pe/institucion/anpd/normas-legales/2018427-29733-2011).
- [Reglamento D. S. 016-2024-JUS](https://www3.congreso.gob.pe/Docs/DGP/DIDP/files/ds_016-2024-jus.pdf), especialmente artículos 6, 18, 69 y 76.
- [ANPD, procedimiento de tutela de derechos ARCO](https://www.gob.pe/9269-iniciar-procedimiento-para-el-ejercicio-de-derechos-de-acceso-rectificacion-cancelacion-y-oposicion).
- [MINJUSDH, entrada en vigencia del nuevo reglamento y consentimiento publicitario](https://www.gob.pe/institucion/minjus/noticias/1137398-nuevo-reglamento-de-proteccion-de-datos-personales-refuerza-el-consentimiento-de-usuarios-para-recibir-llamadas-publicitarias).
- [Supabase, regiones y residencia de datos](https://supabase.com/docs/guides/platform/regions).

## Verificación local

Se comprobó `/privacy` con respuesta HTTP 200, 13 secciones, carga de estilos y enlaces del índice sin destinos faltantes. Se abrió el apartado de derechos y se verificó que su título queda visible debajo de la barra de navegación. En una ventana de 1280 × 720 no se detectó desbordamiento horizontal. Se conservó el diseño compacto compartido con términos y condiciones, sin cambiar los estilos globales ni la funcionalidad de pagos o cuentas.

# Accesos de puerta por evento

## Administración

Desde **Panel Admin → Control de puerta → Configurar puertas**, el administrador
puede configurar hasta cuatro puertas independientes para cada evento. También
hay un enlace **Configurar puertas** en el detalle administrativo del evento.

Cada puerta tiene un nombre, una contraseña propia y un interruptor para
habilitarla o desactivarla. Las contraseñas del mismo evento deben ser distintas,
tener al menos diez caracteres y no superar 72 bytes UTF-8. Solo se guarda su hash;
la contraseña no se muestra ni se puede recuperar. Dejar el campo vacío al editar
conserva la contraseña existente.

El código del evento se genera al aprobar o publicar el evento, si todavía no
tiene uno. El administrador también puede asignarlo desde esta pantalla. No se
admiten códigos repetidos, sin distinguir mayúsculas. Guardar una puerta revoca
sus sesiones anteriores; cambiar el código revoca las sesiones de todas sus puertas.

## Personal de puerta

Desde **Iniciar sesión → Asistencia a puerta**, o directamente desde
`/tickets/validate`, se ingresa únicamente con el código del evento y la contraseña
de la puerta. No se necesita una cuenta de cliente, organizador ni auxiliar.

El acceso dura como máximo doce horas y queda restringido en el servidor al evento
y la puerta autorizados. Solo admite eventos publicados o agotados. Diez intentos
fallidos desde una dirección bloquean nuevos intentos durante quince minutos.

- **Consultar** muestra si una entrada está disponible sin registrar ingreso.
- **Validar y registrar ingreso** comprueba evento, pago confirmado y uso previo.
  Si procede, guarda la hora y el nombre de la puerta en el ticket.
- El bloqueo de la fila del ticket impide que dos puertas confirmen el mismo QR
  simultáneamente. La segunda recibe el estado de entrada ya utilizada.
- Una entrada de otro evento no revela los datos del asistente.
- **Cerrar acceso de puerta** elimina la autorización de esa puerta del navegador.

El campo admite un lector QR que escriba el código o su ingreso manual. Las
contraseñas y el evento autorizado no se envían ocultos en cada validación.

### Escanear desde el teléfono

1. Abrir la web por HTTPS en el navegador del teléfono e ingresar con el código
   del evento y la contraseña de su puerta.
2. Pulsar **Escanear QR** y permitir el uso de cámara. Se prefiere la cámara
   trasera; el navegador elige una disponible si no puede cumplir esa preferencia.
3. Centrar el QR de la entrada. Al leerlo, se copia su código al formulario y se
   detienen la cámara y el lector. La lectura no consulta ni consume la entrada.
4. Pulsar **Consultar** o **Validar y registrar ingreso**. El servidor consulta
   `tickets`, su evento y el estado `COMPLETED` de su compra, utilizando el evento
   autorizado en la sesión de puerta.

El campo **Código de entrada** permite escribirlo directamente, también cuando se
deniega la cámara. Tocarlo detiene la cámara para evitar que una lectura posterior
reemplace lo escrito. Cambiar el código oculta el resultado anterior hasta volver a consultar.
La cámara también se detiene al cerrar el visor, cambiar de pestaña, salir de la
página o enviar el formulario. No se piden permisos al abrir la pantalla.

La cámara no funciona desde una IP de red local servida por HTTP en el teléfono:
se requiere HTTPS. `localhost` permite probar en la misma computadora, pero en el
teléfono representa al propio teléfono, no a la PC. Para uso real móvil se debe
desplegar la actualización en el dominio HTTPS. No hace falta instalar una app.
Se necesita conexión al servidor para consultar o registrar una entrada; no hay
validación offline ni aceptación optimista al leer el QR.

La imagen se procesa en el navegador, sin subir fotogramas. El lector usa
`qr-scanner` 1.4.2, servido desde el proyecto con su motor de respaldo para
navegadores sin `BarcodeDetector`. Se incluyen la licencia MIT y las huellas de
los archivos en `static/js/vendor/qr-scanner-1.4.2/PROVENANCE.md`.

Los tests de cámara utilizan un dispositivo simulado para verificar controles,
permisos, cierre y lectura sin registro automático. La cámara física debe probarse
en el teléfono elegido, con la web publicada por HTTPS y buena iluminación.

## Base de datos y despliegue

La tabla `event_gate_accesses` guarda como máximo cuatro configuraciones por evento.
Los campos `tickets.used_at` y `tickets.used_gate_name` guardan el ingreso.
Los tickets antiguos ya utilizados conservan ese estado, aunque no tengan estos
nuevos datos. La antigua contraseña compartida `events.gate_password` ya no sirve
para abrir el control de puerta y se limpia al configurar una puerta.

En desarrollo MariaDB, Hibernate incorpora los campos con `ddl-auto=update`.
En producción Supabase, aplicar **antes de desplegar el nuevo JAR** la migración
`supabase/migrations/20261018210804_event_gate_accesses.sql`. La migración está
preparada; no se aplica automáticamente al arrancar la aplicación.

La tabla de credenciales está protegida con RLS y sin permisos para `anon` ni
`authenticated`; la usa únicamente el backend con el rol `connectify_app`.
El índice de código de evento rechaza duplicados existentes: corregirlos de forma
controlada antes de repetir la migración, sin cambiar códigos automáticamente.

Este cambio se verifica localmente con pruebas aisladas. Crear las contraseñas
reales corresponde al administrador de cada evento; no hay contraseñas de puerta
predeterminadas ni migración de contraseñas antiguas.

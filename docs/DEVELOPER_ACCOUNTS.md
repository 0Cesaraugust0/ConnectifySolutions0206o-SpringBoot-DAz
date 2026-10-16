# Gestión de cuentas

El acceso Developer abre `/dashboard/developer/accounts`. Su usuario de inicio de sesión es el correo de la cuenta. Las credenciales personales se provisionan de forma privada y no se incluyen en este repositorio.

La consola administra Cliente, Organizador, Diseñador, Administrador y Auxiliar de puerta. Permite crear, editar datos personales y correo, establecer una nueva contraseña, activar, desactivar y eliminar cuentas sin actividad. No permite crear ni modificar otras cuentas Developer o cambiar el rol de una cuenta existente.

Las cuentas con eventos, compras, entradas, intentos de pago, mensajes, consumos de carrito, carritos o favoritos deben desactivarse. No se elimina su historial. El cambio de correo conserva las referencias de identidad de estos registros. Si hay un intento de pago pendiente o en revisión, primero debe resolverse antes de cambiar el correo para conservar la identidad de la confirmación del proveedor.

## Registro y perfiles

`user_accounts.created_at` registra el instante de creación desde esta actualización. Los registros anteriores mantienen fecha desconocida y no se cuentan como nuevos. La consola calcula nuevos hoy y nuevos en los últimos 30 días; el día se interpreta en `America/Lima`.

Un perfil completo contiene nombres, apellido paterno, correo válido, DNI de ocho dígitos y teléfono de nueve a quince dígitos. Para Organizador se añaden nombre comercial o razón social y RUC de once dígitos. El apellido materno es opcional. Este indicador verifica los campos y sus formatos, no la identidad con una fuente externa. Los contadores separan total, activos, nuevos, completos e incompletos y nuevos con perfil completo.

`account_access_audits` conserva quién realizó cada operación, cuenta afectada, tipo de cambio, fecha y descripción; nunca almacena contraseñas. Las contraseñas nuevas se cifran con BCrypt, requieren 12 caracteres y admiten hasta 72 bytes. Los formularios de la consola están protegidos contra CSRF. Los cambios de identidad, contraseña y estado invalidan las sesiones anteriores después de confirmar la transacción.

Validación: pruebas de servicio para roles protegidos, contraseñas, referencias, bloqueo de pagos pendientes, eliminación y conteos. El circuito HTTP se verificó localmente con una cuenta temporal y se retiraron sus datos al finalizar.

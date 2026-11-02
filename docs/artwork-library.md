# Biblioteca de imágenes y arte

La biblioteca `/dashboard/artwork` está disponible únicamente para administración y desarrollo.
Organización y diseño mantienen la carga de imágenes en sus formularios, pero no tienen acceso a la biblioteca.
La antigua ruta `/dashboard/organizer/artwork` queda bloqueada.

Las cargas realizadas al guardar un evento o una propuesta visual se conservan como archivos en
`uploads/presentations`. La base de datos del evento o de su presentación conserva la URL `/uploads/presentations/...`.
La biblioteca consulta esa carpeta: las imágenes nuevas aparecen sin tener que copiarlas al código fuente.
Los videos no forman parte de esta biblioteca.

## Almacenamiento persistente

`connectify.media.directory` permite fijar una carpeta independiente de la versión desplegada.
Por ejemplo, en el VPS puede configurarse `CONNECTIFY_MEDIA_DIRECTORY=/opt/connectify/uploads`,
con permisos de escritura para el usuario del servicio. Las cargas, la biblioteca y el servidor de imágenes
usan la misma configuración. El valor predeterminado sigue siendo `uploads`, relativo al directorio de trabajo.
No se debe vaciar esa carpeta al desplegar un JAR nuevo. El despliegue actual solo reemplaza el JAR.
Las cargas quedan excluidas de Git y deben incluirse en los respaldos junto con la base de datos.

Los archivos del catálogo incluidos con la aplicación siguen en `static/images`. Esta implementación
conserva las cargas en disco local/VPS; no ha trasladado archivos ni creado contenedores en Supabase Storage.

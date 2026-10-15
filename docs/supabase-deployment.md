# Supabase PostgreSQL

El proyecto de producción usa PostgreSQL 17 en Supabase y mantiene las tablas de la aplicación dentro del esquema privado `connectify`. Este esquema no se expone mediante la Data API y revoca el acceso de los roles `anon` y `authenticated`; Spring Boot accede directamente por JDBC.

## Configuración del alojamiento

Activar el perfil de producción y configurar estos secretos en el alojamiento:

```text
SPRING_PROFILES_ACTIVE=prod
SUPABASE_DB_URL=jdbc:postgresql://HOST_DEL_SESSION_POOLER:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres.REFERENCIA_DEL_PROYECTO
SUPABASE_DB_PASSWORD=CONTRASEÑA_DE_LA_BASE_DE_DATOS
```

Debe usarse el **Session pooler** en el puerto `5432`. El Transaction pooler no es apropiado como origen principal de Spring Data JPA porque Hibernate usa declaraciones preparadas en el servidor.

La contraseña se configura como secreto del alojamiento. No debe escribirse en `application-prod.properties` ni guardarse en Git.

## Migraciones

Las migraciones SQL están en `supabase/migrations`. Hibernate usa `ddl-auto=validate` en producción, de modo que valida el esquema pero no lo modifica automáticamente.

## Traslado desde MariaDB local

`scripts/export_mariadb_to_postgres.py` genera un archivo SQL temporal con los datos locales y conserva identificadores y relaciones. Ese archivo contiene información privada y no debe subirse al repositorio.

Después de importar, deben comprobarse los conteos por tabla, las claves foráneas, los valores de las secuencias y el inicio de Spring Boot con el perfil `prod`.

# Variables de entorno para docker-compose.prod.yml

Este documento describe las variables de entorno usadas por [docker-compose.prod.yml](../docker-compose.prod.yml) para el despliegue en produccion.

## Resumen

El archivo usa 3 variables externas:

- `DB_PASSWORD`
- `PLAY_SECRET`
- `POSTGRES_DATA_PATH`

Si no se definen, Docker Compose aplica los valores por defecto configurados en el propio archivo.

## Tabla de variables

| Variable | Servicio(s) | Uso | Valor por defecto | Requerida en produccion |
|---|---|---|---|---|
| `DB_PASSWORD` | `postgres`, `app` | Password de PostgreSQL (`POSTGRES_PASSWORD` y `SLICK_DBS_DEFAULT_DB_PASSWORD`) | `reactive_password` | Si |
| `PLAY_SECRET` | `app` | Clave de firma/cifrado de Play (`PLAY_CRYPTO_SECRET`) | `changeme_in_production_use_strong_secret_key_32chars` | Si |
| `POSTGRES_DATA_PATH` | `postgres` (volumen) | Ruta local bind para persistir datos PostgreSQL | `.//postgres_data` | Recomendado |

## Detalle por variable

### DB_PASSWORD

- Aplica a:
  - `services.postgres.environment.POSTGRES_PASSWORD`
  - `services.app.environment.SLICK_DBS_DEFAULT_DB_PASSWORD`
- Objetivo:
  - Mantener sincronizada la credencial entre la base de datos y la aplicacion.
- Riesgo si no se cambia:
  - El default `reactive_password` es predecible.

Recomendacion:

- Usar un valor largo y aleatorio (minimo 24 caracteres).
- Evitar caracteres que rompan shell si se exporta por terminal sin comillas.

Ejemplo:

```bash
DB_PASSWORD="9xW3k!2qZpT7mL4rV8sN1dF6uH"
```

### PLAY_SECRET

- Aplica a:
  - `services.app.environment.PLAY_CRYPTO_SECRET`
- Objetivo:
  - Firma de cookies/sesion y funciones criptograficas de Play.
- Riesgo si no se cambia:
  - El default `changeme_in_production_use_strong_secret_key_32chars` no es seguro para produccion.

Recomendacion:

- Definir al menos 32 caracteres aleatorios.
- Rotar la clave en ventanas de mantenimiento controladas.

Ejemplo:

```bash
PLAY_SECRET="x7vF1sL3qN9kP2rT6yU4wA8mD5cH0jBz"
```

### POSTGRES_DATA_PATH

- Aplica a:
  - `volumes.postgres_data.driver_opts.device`
- Objetivo:
  - Elegir donde se guardan los datos persistentes de PostgreSQL en el host.
- Si no se define:
  - Usa `.//postgres_data` (ruta relativa al directorio del compose).

Recomendacion:

- En produccion, usar una ruta absoluta gestionada por infraestructura, por ejemplo:

```bash
POSTGRES_DATA_PATH=/srv/reactive-manifesto/postgres-data
```

## Como definir variables

Docker Compose lee automaticamente un archivo `.env` en el mismo directorio que [docker-compose.prod.yml](../docker-compose.prod.yml).

### Opcion 1: archivo .env (recomendado)

Crear un `.env` con:

```dotenv
DB_PASSWORD=REEMPLAZAR_CON_PASSWORD_SEGURO
PLAY_SECRET=REEMPLAZAR_CON_SECRET_SEGURO_32_CHARS_O_MAS
POSTGRES_DATA_PATH=/srv/reactive-manifesto/postgres-data
```

### Opcion 2: export en shell

```bash
# 1) Definir password DB robusto (32 chars)
export DB_PASSWORD="cN7vQ2mL9rT4xP8kH5sD1wF6yB3uJ0zA"

# 2) Definir secreto Play robusto (>= 32 chars)
export PLAY_SECRET="Y4kP9sT2mN7vQ5xD1hF8rL3wB6uJ0zAc"

# 3) Definir ruta persistente para PostgreSQL
export POSTGRES_DATA_PATH="/srv/reactive-manifesto/postgres-data"

# 4) Crear carpeta con permisos restringidos
sudo mkdir -p "$POSTGRES_DATA_PATH"
sudo chown -R "$USER":"$USER" "$POSTGRES_DATA_PATH"
chmod 700 "$POSTGRES_DATA_PATH"

# 5) Levantar stack de produccion
docker compose -f docker-compose.prod.yml up -d
```

Si prefieres generar secretos en el momento en lugar de escribirlos manualmente:

```bash
# Solo caracteres alfanumericos para evitar problemas de escape
export DB_PASSWORD="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)"
export PLAY_SECRET="$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 48)"
export POSTGRES_DATA_PATH="/srv/reactive-manifesto/postgres-data"

sudo mkdir -p "$POSTGRES_DATA_PATH"
sudo chown -R "$USER":"$USER" "$POSTGRES_DATA_PATH"
chmod 700 "$POSTGRES_DATA_PATH"

docker compose -f docker-compose.prod.yml up -d
```

## Verificacion rapida

Para confirmar que Compose resuelve las variables como esperas:

```bash
docker compose -f docker-compose.prod.yml config
```

Esto imprime la configuracion final efectiva con defaults + overrides.

## Notas de seguridad

- No subir `.env` real al repositorio.
- No usar defaults en produccion.
- Considerar gestion de secretos (Docker secrets, Vault, o variable injection desde CI/CD).

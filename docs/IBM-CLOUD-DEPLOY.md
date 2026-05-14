# Deploy en IBM Cloud — Guía de Re-deploy

## Infraestructura actual

| Componente         | Detalle                                                                 |
|--------------------|-------------------------------------------------------------------------|
| **Plataforma**     | IBM Code Engine (serverless containers)                                 |
| **Región**         | `us-south` (Dallas)                                                     |
| **Proyecto CE**    | `reactive-systems-project`                                              |
| **Aplicación CE**  | `reactive-systems-app`                                                  |
| **URL pública**    | https://reactive-systems-app.29uatxtikeu0.us-south.codeengine.appdomain.cloud |
| **Registry**       | `us.icr.io/reactive-systems-ns/reactive-systems:latest`                 |
| **Base de datos**  | Supabase PostgreSQL (Session Pooler — IPv4, puerto 5432)                |
| **Auto-scaling**   | 1–3 instancias según tráfico                                            |
| **RAM / CPU**      | 1 GB / 0.5 vCPU por instancia                                          |

---

## Requisitos previos

- [IBM Cloud CLI](https://cloud.ibm.com/docs/cli) instalado (`ibmcloud`)
- Plugins instalados: `code-engine` y `container-registry`
- Docker instalado y en ejecución
- Sesión activa en IBM Cloud

### Instalar CLI y plugins (primera vez)

```bash
# Instalar IBM Cloud CLI
curl -fsSL https://clis.cloud.ibm.com/install/linux | sh

# Instalar plugins
ibmcloud plugin install code-engine -f
ibmcloud plugin install container-registry -f
```

---

## Login en IBM Cloud

### Opción A — SSO (recomendado)

```bash
ibmcloud login --sso --no-region
# Abrí la URL que imprime en el browser, copiá el one-time code y pegalo en la terminal
```

### Opción B — API Key

```bash
ibmcloud login --apikey <TU_API_KEY> --no-region
```

### Configurar target

```bash
ibmcloud target -r us-south -g Default
ibmcloud ce project select --name reactive-systems-project
```

---

## Re-deploy completo (build + push + deploy)

Usá este flujo cuando cambiás código fuente:

```bash
# 1. Autenticarse en el Container Registry
ibmcloud cr login

# 2. Build de la imagen Docker
docker build -t us.icr.io/reactive-systems-ns/reactive-systems:latest .

# 3. Push al IBM Container Registry
docker push us.icr.io/reactive-systems-ns/reactive-systems:latest

# 4. Actualizar la aplicación en Code Engine
ibmcloud ce application update \
  --name reactive-systems-app \
  --image us.icr.io/reactive-systems-ns/reactive-systems:latest
```

> El paso 4 dispara un rolling update — sin downtime.

---

## Re-deploy rápido (solo actualizar variables de entorno)

Si solo necesitás cambiar configuración sin rebuild:

```bash
ibmcloud ce project select --name reactive-systems-project

ibmcloud ce application update \
  --name reactive-systems-app \
  --env NOMBRE_VARIABLE="nuevo_valor"
```

---

## Variables de entorno en producción

| Variable              | Descripción                                        |
|-----------------------|----------------------------------------------------|
| `APPLICATION_SECRET`  | Secret key de Play Framework (base64, 32 bytes)    |
| `DATABASE_URL`        | JDBC URL del pooler de Supabase (Session mode)     |
| `DATABASE_USER`       | Usuario de Supabase (`postgres.<project-ref>`)     |
| `DATABASE_PASSWORD`   | Contraseña de la base de datos                     |
| `DB_URL`              | Alias de `DATABASE_URL`                            |
| `DB_USER`             | Alias de `DATABASE_USER`                           |
| `DB_PASSWORD`         | Alias de `DATABASE_PASSWORD`                       |
| `JAVA_OPTS`           | Opciones JVM + hosts permitidos (AllowedHostsFilter) |

### Formato de DATABASE_URL (Session Pooler de Supabase)

```
jdbc:postgresql://aws-1-us-west-1.pooler.supabase.com:5432/postgres?sslmode=require
```

> **Importante**: usar el **Session Pooler** (no el Direct Connection), porque Code Engine usa redes IPv4 y el host directo de Supabase (`db.*.supabase.co`) resuelve en IPv6.

### Generar un nuevo APPLICATION_SECRET

```bash
openssl rand -base64 32
```

---

## Monitoreo y logs

```bash
# Ver logs en tiempo real
ibmcloud ce application logs -f -n reactive-systems-app

# Ver estado de la aplicación
ibmcloud ce application get -n reactive-systems-app

# Ver eventos del sistema
ibmcloud ce application events -n reactive-systems-app

# Ver instancias activas
ibmcloud ce application get -n reactive-systems-app | grep -A5 "Instances"
```

---

## Rollback a una revisión anterior

```bash
# Listar revisiones
ibmcloud ce revision list --application reactive-systems-app

# Hacer rollback a una revisión específica
ibmcloud ce application update \
  --name reactive-systems-app \
  --revision reactive-systems-app-0000X
```

---

## Escalar manualmente

```bash
# Cambiar límites de escala
ibmcloud ce application update \
  --name reactive-systems-app \
  --min-scale 1 \
  --max-scale 5
```

---

## Consola web de IBM Cloud

- **Code Engine**: https://cloud.ibm.com/codeengine/project/us-south/ca0db358-fee6-430d-a6f5-49040846391e/application/reactive-systems-app/configuration
- **Container Registry**: https://cloud.ibm.com/registry/namespaces

---

## Solución de problemas frecuentes

### `AllowedHostsFilter — Host not allowed`

Play Framework bloquea el dominio. Se resuelve agregando el host en `JAVA_OPTS`:

```bash
ibmcloud ce application update \
  --name reactive-systems-app \
  --env JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Dplay.server.pidfile.path=/dev/null \
    -Dplay.filters.hosts.allowed.0=reactive-systems-app.29uatxtikeu0.us-south.codeengine.appdomain.cloud \
    -Dplay.filters.hosts.allowed.1=localhost"
```

### `Network is unreachable` al conectar a Supabase

Usá el **Session Pooler** de Supabase en vez de la conexión directa. Ver sección *Variables de entorno*.

### `UNAUTHORIZED` al hacer push al registry

```bash
ibmcloud cr login
```

### Registry secret expirado en Code Engine

```bash
# Crear nueva API key
ibmcloud iam api-key-create ce-registry-key --output json | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['apikey'])" > /tmp/key.txt

# Actualizar el secret
ibmcloud ce registry delete --name ibmcr-secret -f
ibmcloud ce registry create --name ibmcr-secret \
  --server us.icr.io \
  --username iamapikey \
  --password "$(cat /tmp/key.txt)"
```

# 🚀 Guía de Deployment - Reactive Manifesto

Esta guía te ayudará a deployar tu aplicación Reactive Manifesto en producción con un dominio personalizado.

## 📋 Tabla de Contenidos

- [Opciones de Deployment](#opciones-de-deployment)
- [Deployment en Render.com (Recomendado)](#deployment-en-rendercom-recomendado)
- [Configuración de Dominio Personalizado](#configuración-de-dominio-personalizado)
- [Variables de Entorno](#variables-de-entorno)
- [Base de Datos](#base-de-datos)
- [Troubleshooting](#troubleshooting)

## 🎯 Opciones de Deployment

### Render.com (Recomendado) ⭐
- ✅ Free tier disponible
- ✅ PostgreSQL incluido
- ✅ SSL automático
- ✅ Dominio personalizado gratuito
- ✅ Deploy automático desde GitHub
- ✅ Ideal para Play Framework

### Alternativas
- **Railway.app**: Similar a Render, free tier generoso
- **Fly.io**: Excelente performance global
- **Heroku**: Opciones limitadas en free tier

## 🚀 Deployment en Render.com (Recomendado)

### Paso 1: Preparar el Repositorio

Tu proyecto ya incluye los archivos necesarios:
- ✅ `render.yaml` - Configuración de infraestructura
- ✅ `conf/application.prod.conf` - Configuración de producción
- ✅ PostgreSQL driver en `build.sbt`

### Paso 2: Crear Cuenta en Render

1. Ve a [https://render.com](https://render.com)
2. Regístrate con tu cuenta de GitHub
3. Autoriza a Render para acceder a tus repositorios

### Paso 3: Crear Nuevo Web Service

#### Opción A: Deploy con Blueprint (Automático)

1. En el dashboard de Render, haz clic en **"New +"** → **"Blueprint"**
2. Conecta tu repositorio: `federicopfund/Reactive-Manifiesto`
3. Render detectará automáticamente `render.yaml` y creará:
   - ✅ Web Service (aplicación Play Framework)
   - ✅ PostgreSQL Database
   - ✅ Variables de entorno configuradas
4. Haz clic en **"Apply"**
5. Espera a que el deployment termine (~5-10 minutos)

#### Opción B: Deploy Manual

Si prefieres configurar manualmente:

1. **Crear Base de Datos PostgreSQL:**
   - Dashboard → **"New +"** → **"PostgreSQL"**
   - Name: `reactive-manifesto-db`
   - Database: `reactive_manifesto`
   - User: `reactive_user`
   - Plan: **Free**
   - Haz clic en **"Create Database"**

2. **Crear Web Service:**
   - Dashboard → **"New +"** → **"Web Service"**
   - Conecta tu repositorio GitHub
   - Configuración:
     - **Name:** `reactive-manifesto`
     - **Region:** Elige el más cercano a tus usuarios
     - **Branch:** `main`
     - **Runtime:** `Java`
     - **Build Command:**
       ```bash
       sbt clean compile stage
       ```
     - **Start Command:**
       ```bash
       ./target/universal/stage/bin/web -Dhttp.port=$PORT -Dplay.http.secret.key=$APPLICATION_SECRET -Dconfig.file=conf/application.prod.conf
       ```
     - **Plan:** Free

3. **Configurar Variables de Entorno:**
   - En el Web Service, ve a **"Environment"**
   - Agrega las siguientes variables:
     ```
     APPLICATION_SECRET=<generar-clave-segura-aqui>
     DATABASE_URL=<copiar-de-la-base-de-datos>
     JAVA_OPTS=-Xmx512m -Xms256m
     SBT_OPTS=-Xmx1024m -Xms512m
     ```

### Paso 4: Generar APPLICATION_SECRET

Genera una clave segura de 64 caracteres:

```bash
# Opción 1: Usando OpenSSL
openssl rand -base64 48

# Opción 2: Usando sbt (desde el proyecto)
sbt playGenerateSecret

# Opción 3: Online
# https://www.browserling.com/tools/random-string
```

### Paso 5: Obtener DATABASE_URL

1. Ve a tu PostgreSQL Database en Render
2. Copia la **"Internal Database URL"** o **"External Database URL"**
3. El formato es:
   ```
   postgresql://user:password@hostname:5432/database
   ```

### Paso 6: Verificar el Deployment

1. Render comenzará a buildear tu aplicación
2. Puedes ver los logs en tiempo real
3. Una vez completado, tu app estará disponible en:
   ```
   https://reactive-manifesto.onrender.com
   ```
   (o el nombre que hayas elegido)

## 🌐 Configuración de Dominio Personalizado

### Opción 1: Usar Dominio de Render (Gratuito)

Tu aplicación automáticamente tiene un dominio:
```
https://<tu-servicio>.onrender.com
```

**Ventajas:**
- ✅ SSL automático
- ✅ Sin configuración adicional
- ✅ Gratis

### Opción 2: Dominio Personalizado

#### Paso 1: Comprar un Dominio

Proveedores recomendados:
- **Namecheap**: ~$10/año
- **Google Domains**: ~$12/año
- **Cloudflare**: Precio al costo
- **Porkbun**: ~$9/año

#### Paso 2: Configurar DNS

1. En Render, ve a tu Web Service → **"Settings"** → **"Custom Domain"**
2. Haz clic en **"Add Custom Domain"**
3. Ingresa tu dominio, por ejemplo: `www.reactivemanifesto.com`

Render te mostrará registros DNS para configurar:

**Para dominio raíz (`reactivemanifesto.com`):**
```
Type: A
Name: @
Value: <IP-de-Render> (ej: 216.24.57.1)
```

**Para subdominio (`www.reactivemanifesto.com`):**
```
Type: CNAME
Name: www
Value: reactive-manifesto.onrender.com
```

#### Paso 3: Configurar en tu Proveedor DNS

**Ejemplo con Namecheap:**

1. Login en Namecheap
2. Dashboard → **"Domain List"** → **"Manage"**
3. **"Advanced DNS"** tab
4. Agrega los registros:
   - **A Record:**
     - Host: `@`
     - Value: `<IP-de-Render>`
     - TTL: Automatic
   - **CNAME Record:**
     - Host: `www`
     - Value: `reactive-manifesto.onrender.com`
     - TTL: Automatic

**Ejemplo con Cloudflare:**

1. Login en Cloudflare
2. Selecciona tu dominio
3. **"DNS"** → **"Records"**
4. Agrega los registros (deshabilita proxy naranja temporalmente)
5. Una vez que Render verifique el dominio, puedes habilitar el proxy

#### Paso 4: Verificar

1. Render automáticamente verificará tu dominio (puede tomar hasta 24-48 horas)
2. Una vez verificado, Render proveerá **SSL/TLS automático** con Let's Encrypt
3. Tu sitio estará disponible en:
   ```
   https://www.reactivemanifesto.com
   https://reactivemanifesto.com
   ```

#### Paso 5: Actualizar Allowed Hosts

Para mayor seguridad, agrega tu dominio personalizado a la lista de hosts permitidos.

Edita `conf/application.prod.conf` y agrega tu dominio:

```hocon
play.filters.hosts {
  allowed = [".onrender.com", "localhost", ".tudominio.com", "www.tudominio.com"]
  # Por ejemplo: ".reactivemanifesto.com", "reactivemanifesto.com"
}
```

**Alternativamente**, puedes usar una variable de entorno para mayor flexibilidad:

1. En Render, agrega variable de entorno:
   ```
   ALLOWED_HOSTS=.tudominio.com,tudominio.com
   ```

2. En `application.prod.conf`:
   ```hocon
   play.filters.hosts {
     allowed = [".onrender.com", "localhost"]
     allowed = ${?ALLOWED_HOSTS}
   }
   ```

Commit y push los cambios. Render auto-deploiará.

## 🔐 Variables de Entorno

### Variables Requeridas

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `APPLICATION_SECRET` | Clave secreta de Play Framework | `changeme123456789...` (64+ caracteres) |
| `DATABASE_URL` | URL de conexión PostgreSQL | `postgresql://user:pass@host:5432/db` |
| `JAVA_OPTS` | Opciones de JVM | `-Xmx512m -Xms256m` |
| `SBT_OPTS` | Opciones de SBT (build) | `-Xmx1024m -Xms512m` |

### Variables Opcionales

| Variable | Descripción | Default |
|----------|-------------|---------|
| `PORT` | Puerto HTTP | Automático en Render |
| `PLAY_HTTP_PORT` | Puerto alternativo | `9000` |

### Configurar Variables en Render

1. Web Service → **"Environment"**
2. Haz clic en **"Add Environment Variable"**
3. Agrega cada variable con su valor
4. **Save Changes**
5. Render redeploy automáticamente

## 💾 Base de Datos

### PostgreSQL en Render

**Características:**
- ✅ 256 MB de almacenamiento (free tier)
- ✅ Backups automáticos (últimos 7 días)
- ✅ Conexión interna de alta velocidad
- ✅ SSL habilitado por defecto

### Migraciones (Evolutions)

La aplicación usa **Play Evolutions** para gestionar el schema de base de datos.

**Primera vez:**
1. Render ejecutará las evolutions automáticamente
2. Revisa los logs para confirmar:
   ```
   [info] Database 'default' is up to date
   ```

**Agregar nuevas evolutions:**
1. Crea archivo en `conf/evolutions/default/2.sql` (incrementa el número)
2. Sigue el formato:
   ```sql
   # --- !Ups
   
   ALTER TABLE contacts ADD COLUMN phone VARCHAR(20);
   
   # --- !Downs
   
   ALTER TABLE contacts DROP COLUMN phone;
   ```
3. Commit y push
4. Render aplicará automáticamente

### Conexión Manual a la Base de Datos

Desde tu terminal local:

```bash
# Obtén la External Database URL desde Render
psql <EXTERNAL_DATABASE_URL>

# O usando parámetros individuales
psql -h <hostname> -U <user> -d <database> -p 5432
```

### Backup Manual

```bash
# Exportar
pg_dump <DATABASE_URL> > backup.sql

# Restaurar
psql <DATABASE_URL> < backup.sql
```

## 🔍 Monitoring y Logs

### Ver Logs en Tiempo Real

1. Dashboard de Render → Tu Web Service
2. **"Logs"** tab
3. Ver logs en streaming

### Logs Útiles

**Inicio exitoso:**
```
[info] play.api.Play - Application started (Prod)
[info] play.core.server.AkkaHttpServer - Listening for HTTP on /0.0.0.0:10000
```

**Database conectada:**
```
[info] database.default - Starting connection pool
[info] database.default - Database 'default' is up to date
```

### Metrics

Render Free tier no incluye métricas avanzadas, pero puedes:
- Ver uso de memoria y CPU en el dashboard
- Configurar alerts por email
- Integrar con servicios externos (New Relic, DataDog)

## 🛠️ Troubleshooting

### Error: "Application secret not set"

**Solución:**
```bash
# Genera nuevo secret
openssl rand -base64 48

# Agrégalo en Render Environment variables
APPLICATION_SECRET=<tu-secret>
```

### Error: "Cannot connect to database"

**Solución:**
1. Verifica que `DATABASE_URL` esté correcta
2. Usa la **Internal Database URL** (más rápida)
3. Asegúrate que el formato sea correcto:
   ```
   postgresql://user:password@host:5432/database
   ```

### Error: "Out of memory"

**Solución:**
Ajusta `JAVA_OPTS` en variables de entorno:
```
JAVA_OPTS=-Xmx512m -Xms256m -XX:MaxMetaspaceSize=256m
```

### Build Falla

**Solución:**
1. Revisa los logs de build
2. Asegúrate que `build.sbt` esté correcto
3. Verifica que todas las dependencias estén disponibles
4. Prueba el build localmente:
   ```bash
   sbt clean compile stage
   ```

### Dominio No Resuelve

**Solución:**
1. Verifica registros DNS con:
   ```bash
   dig www.tudominio.com
   nslookup www.tudominio.com
   ```
2. Espera 24-48 horas para propagación completa
3. Limpia caché DNS:
   ```bash
   # Windows
   ipconfig /flushdns
   
   # Mac
   sudo dscacheutil -flushcache
   
   # Linux
   sudo systemd-resolve --flush-caches
   ```

### SSL Certificate No Se Genera

**Solución:**
1. Asegúrate que DNS apunte correctamente a Render
2. Espera 24 horas
3. En Render, **"Custom Domain"** → **"Verify"**
4. Si persiste, contacta soporte de Render

### Aplicación Lenta o Time Out

**Posibles causas:**
- Free tier tiene cold starts (primera request lenta después de inactividad)
- Queries lentas a la base de datos
- Demasiadas conexiones abiertas

**Soluciones:**
- Usa un tier pago para evitar cold starts
- Optimiza queries y agrega índices
- Ajusta pool de conexiones en `application.prod.conf`

## 📊 Performance Tips

### Optimizar Cold Starts

1. **Mantén la app "caliente":**
   - Usa servicios como [UptimeRobot](https://uptimerobot.com/) para ping cada 5 minutos
   - Configura un cron job que haga requests periódicas

2. **Reduce tamaño del build:**
   ```scala
   // build.sbt
   javaOptions in Universal ++= Seq(
     "-J-Xms256m",
     "-J-Xmx512m",
     "-J-XX:MaxMetaspaceSize=256m"
   )
   ```

### CDN para Assets Estáticos

Usa Cloudflare (gratis) como CDN:
1. Agrega tu dominio a Cloudflare
2. Actualiza nameservers en tu registrar
3. Habilita cache para `/assets/*`
4. Habilita Brotli compression

### Database Connection Pooling

Ajusta en `application.prod.conf`:
```hocon
slick.dbs.default.db {
  numThreads = 5
  maxConnections = 5
  minConnections = 2
  connectionTimeout = 5000
}
```

## 🎉 Checklist de Post-Deployment

- [ ] Aplicación accesible en dominio de Render
- [ ] Base de datos PostgreSQL funcionando
- [ ] Evolutions aplicadas correctamente
- [ ] SSL/TLS activo (https://)
- [ ] Formulario de contacto funcional
- [ ] Logs sin errores críticos
- [ ] Variables de entorno configuradas
- [ ] Dominio personalizado configurado (opcional)
- [ ] DNS propagado y verificado
- [ ] Monitoring básico configurado

## 📚 Recursos Adicionales

- **Render Documentation:** https://render.com/docs
- **Play Framework Production:** https://www.playframework.com/documentation/3.0.x/Production
- **PostgreSQL on Render:** https://render.com/docs/databases
- **Custom Domains:** https://render.com/docs/custom-domains
- **SSL/TLS:** https://render.com/docs/tls

## 🆘 Soporte

Si encuentras problemas:

1. **Render Community:** https://community.render.com/
2. **Play Framework Discord:** https://discord.gg/playframework
3. **Stack Overflow:** Tag `playframework` + `render`
4. **GitHub Issues:** Abre un issue en tu repositorio

---

**¡Felicidades! Tu aplicación Reactive Manifesto está ahora en producción! 🎉**

Para actualizaciones futuras, simplemente haz push a tu rama `main` y Render auto-deploiará los cambios.

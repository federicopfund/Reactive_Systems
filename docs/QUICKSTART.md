# 🚀 Quick Start - Deploy en 5 Minutos

Guía rápida para deployar tu aplicación Reactive Manifesto.

## ✅ Pre-requisitos

- [ ] Cuenta de GitHub
- [ ] Repositorio pusheado a GitHub
- [ ] 5 minutos de tu tiempo

## 🎯 Opción 1: Render.com (Recomendado - Más Fácil)

### 1. Crear cuenta en Render

1. Ve a [https://render.com/](https://render.com/)
2. Haz clic en **"Get Started"**
3. Regístrate con tu cuenta de GitHub

### 2. Deploy con un clic

1. En Render dashboard, haz clic en **"New +"**
2. Selecciona **"Blueprint"**
3. Conecta tu repositorio: `federicopfund/Reactive-Manifiesto`
4. Render detectará `render.yaml` automáticamente
5. Haz clic en **"Apply"**

**¡Eso es todo!** 🎉

Tu app estará disponible en:
```
https://reactive-manifesto.onrender.com
```

### 3. Agregar dominio personalizado (Opcional)

1. En tu servicio → **"Settings"** → **"Custom Domain"**
2. Ingresa tu dominio: `www.tudominio.com`
3. Configura los registros DNS:
   ```
   Type: CNAME
   Name: www
   Value: reactive-manifesto.onrender.com
   ```
4. Espera 24-48 horas para propagación DNS

SSL se configurará automáticamente ✅

---

## 🎯 Opción 2: Railway.app (También Fácil)

### 1. Crear cuenta

1. Ve a [https://railway.app/](https://railway.app/)
2. Regístrate con GitHub

### 2. Deploy

1. Dashboard → **"New Project"**
2. **"Deploy from GitHub repo"**
3. Selecciona `Reactive-Manifiesto`
4. Railway detectará que es un proyecto Scala/Play

### 3. Agregar PostgreSQL

1. Tu proyecto → **"New"** → **"Database"** → **"PostgreSQL"**
2. Railway conectará automáticamente la DB

### 4. Configurar variables

1. **"Variables"** tab
2. Agrega:
   ```
   APPLICATION_SECRET=<genera-con-openssl-rand-base64-48>
   ```

**¡Listo!** Tu app estará en:
```
https://reactive-manifesto-production.up.railway.app
```

---

## 🎯 Opción 3: Fly.io (Más Control)

### 1. Instalar flyctl

```bash
# Mac/Linux
curl -L https://fly.io/install.sh | sh

# Windows
iwr https://fly.io/install.ps1 -useb | iex
```

### 2. Login y setup

```bash
flyctl auth login
cd /path/to/Reactive-Manifiesto
flyctl launch
```

### 3. Configurar

```bash
# Agregar PostgreSQL
flyctl postgres create

# Conectar a tu app
flyctl postgres attach <postgres-app-name>

# Configurar secrets
flyctl secrets set APPLICATION_SECRET=$(openssl rand -base64 48)

# Deploy
flyctl deploy
```

---

## 📊 Comparación Rápida

| Plataforma | Dificultad | Free Tier | SSL | Dominio Custom | Cold Starts |
|------------|------------|-----------|-----|----------------|-------------|
| **Render** | ⭐ Fácil | ✅ 750h/mes | ✅ Auto | ✅ Gratis | Sí (45s) |
| **Railway** | ⭐ Fácil | ✅ $5 credit/mes | ✅ Auto | ✅ Gratis | Mínimos |
| **Fly.io** | ⭐⭐ Medio | ✅ Generoso | ✅ Auto | ✅ Gratis | No |

---

## 🔐 Generar APPLICATION_SECRET

Elige uno:

```bash
# Opción 1: OpenSSL (recomendado)
openssl rand -base64 48

# Opción 2: Desde el proyecto
sbt playGenerateSecret

# Opción 3: Node.js
node -e "console.log(require('crypto').randomBytes(64).toString('base64'))"
```

Copia el resultado y úsalo como `APPLICATION_SECRET`.

---

## 🌐 Configurar Dominio Personalizado

### Paso 1: Comprar dominio

Proveedores baratos:
- [Namecheap](https://namecheap.com) - ~$10/año
- [Porkbun](https://porkbun.com) - ~$9/año
- [Cloudflare](https://cloudflare.com) - Precio al costo

### Paso 2: Configurar DNS

En tu proveedor DNS, agrega:

**Para Render/Railway:**
```
Type: CNAME
Name: www
Value: <tu-app>.onrender.com (o railway.app)
TTL: 3600
```

**Para dominio raíz (@):**
```
Type: CNAME (o ALIAS si tu proveedor lo soporta)
Name: @
Value: <tu-app>.onrender.com
TTL: 3600
```

### Paso 3: Verificar

```bash
# Ver si DNS propagó
dig www.tudominio.com

# O usa
https://dnschecker.org
```

---

## ✅ Checklist Post-Deployment

- [ ] App accesible en URL pública
- [ ] SSL/HTTPS funcionando (candado verde)
- [ ] Base de datos conectada
- [ ] Formulario de contacto funcional
- [ ] No hay errores en los logs
- [ ] Dominio personalizado configurado (si aplica)

---

## 🆘 Problemas Comunes

### Build falla

```bash
# Verifica localmente primero
sbt clean compile stage
```

Si compila localmente pero falla en deployment, revisa:
- Versión de Java (debe ser 11+)
- Variables de entorno configuradas
- Logs de la plataforma

### No puedo acceder a la app

1. Verifica que el deployment terminó exitosamente
2. Revisa los logs por errores
3. Confirma que el puerto está correctamente configurado
4. Espera ~2 minutos después del deploy (inicialización)

### Dominio no resuelve

1. Verifica registros DNS: `dig tudominio.com`
2. Espera 24-48h para propagación completa
3. Limpia caché DNS local:
   ```bash
   # Mac
   sudo dscacheutil -flushcache
   
   # Windows
   ipconfig /flushdns
   
   # Linux
   sudo systemd-resolve --flush-caches
   ```

---

## 📚 Más Información

Para detalles completos, consulta:
- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Guía completa de deployment
- **[README.md](README.md)** - Documentación del proyecto

---

## 🎉 ¡Éxito!

Tu aplicación Reactive Manifesto está ahora en producción y accesible públicamente.

**URLs útiles:**
- **Render Dashboard:** https://dashboard.render.com/
- **Railway Dashboard:** https://railway.app/dashboard
- **Fly.io Dashboard:** https://fly.io/dashboard

**Para actualizar:** Simplemente haz push a GitHub y el deployment será automático.

```bash
git add .
git commit -m "Actualización"
git push origin main
```

---

¿Necesitas ayuda? Consulta [DEPLOYMENT.md](DEPLOYMENT.md) para troubleshooting detallado.

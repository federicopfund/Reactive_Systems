#!/bin/bash

# Script de deployment para Reactive Manifesto
# Este script verifica que todo esté listo para deployment

set -e

echo "🚀 Verificando preparación para deployment..."
echo ""

# Verificar que sbt esté instalado
if ! command -v sbt &> /dev/null; then
    echo "❌ Error: SBT no está instalado"
    echo "   Instala SBT desde: https://www.scala-sbt.org/download.html"
    exit 1
fi
echo "✅ SBT instalado"

# Verificar versión de Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java no está instalado"
    echo "   Se requiere Java 17 o superior"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "❌ Error: Java version $JAVA_VERSION es muy antigua"
    echo "   Se requiere Java 11 o superior (recomendado: Java 17)"
    exit 1
fi
echo "✅ Java $JAVA_VERSION instalado"

# Verificar que existan archivos de configuración
if [ ! -f "render.yaml" ]; then
    echo "❌ Error: render.yaml no encontrado"
    exit 1
fi
echo "✅ render.yaml encontrado"

if [ ! -f "conf/application.prod.conf" ]; then
    echo "❌ Error: conf/application.prod.conf no encontrado"
    exit 1
fi
echo "✅ application.prod.conf encontrado"

# Verificar build.sbt
if [ ! -f "build.sbt" ]; then
    echo "❌ Error: build.sbt no encontrado"
    exit 1
fi
echo "✅ build.sbt encontrado"

# Verificar que PostgreSQL driver esté en build.sbt
if ! grep -q "postgresql" build.sbt; then
    echo "⚠️  Advertencia: PostgreSQL driver no encontrado en build.sbt"
    echo "   Agrega: \"org.postgresql\" % \"postgresql\" % \"42.7.2\""
fi

echo ""
echo "🧪 Ejecutando tests..."
if sbt test; then
    echo "✅ Tests pasaron correctamente"
else
    echo "❌ Algunos tests fallaron"
    echo "   Revisa los errores antes de deployar"
    exit 1
fi

echo ""
echo "🔨 Verificando que el proyecto compile..."
if sbt clean compile; then
    echo "✅ Proyecto compila correctamente"
else
    echo "❌ Error de compilación"
    echo "   Corrige los errores antes de deployar"
    exit 1
fi

echo ""
echo "📦 Creando build de producción (stage)..."
if sbt stage; then
    echo "✅ Build de producción creado exitosamente"
else
    echo "❌ Error creando build de producción"
    exit 1
fi

echo ""
echo "✨ ¡Todo listo para deployment!"
echo ""
echo "📋 Próximos pasos:"
echo "   1. Push tus cambios a GitHub:"
echo "      git add ."
echo "      git commit -m 'Preparado para deployment'"
echo "      git push origin main"
echo ""
echo "   2. Ve a Render.com y:"
echo "      - Crea un nuevo Blueprint"
echo "      - Conecta tu repositorio"
echo "      - Render detectará automáticamente render.yaml"
echo ""
echo "   3. Configura las variables de entorno:"
echo "      - APPLICATION_SECRET (genera con: openssl rand -base64 48)"
echo "      - DATABASE_URL (se auto-configura con Render PostgreSQL)"
echo ""
echo "   📚 Guía completa: Ver DEPLOYMENT.md"
echo ""

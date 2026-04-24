#!/bin/bash
# Script de validación del Dockerfile (sin construir)
# Este script verifica la estructura y configuración del Dockerfile

set -e

echo "========================================="
echo "Validación del Dockerfile"
echo "========================================="
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DOCKERFILE="Dockerfile"
ERRORS=0
WARNINGS=0

# Función para verificar
check() {
    local description="$1"
    local pattern="$2"
    local required="$3"

    if grep -q "$pattern" "$DOCKERFILE"; then
        echo -e "${GREEN}✓${NC} $description"
        return 0
    else
        if [ "$required" = "required" ]; then
            echo -e "${RED}✗${NC} $description"
            ERRORS=$((ERRORS + 1))
            return 1
        else
            echo -e "${YELLOW}⚠${NC} $description (opcional)"
            WARNINGS=$((WARNINGS + 1))
            return 1
        fi
    fi
}

echo "1. Verificando estructura multi-stage..."
check "Stage builder definido" "FROM.*AS builder" "required"
check "Stage runtime (segundo FROM)" "^FROM eclipse-temurin:17-jre" "required"
echo ""

echo "2. Verificando imágenes base..."
check "Usa Eclipse Temurin 17 JDK para builder" "eclipse-temurin:17-jdk" "required"
check "Usa Eclipse Temurin 17 JRE para runtime" "eclipse-temurin:17-jre" "required"
echo ""

echo "3. Verificando instalación de sbt..."
check "Instala sbt" "sbt" "required"
echo ""

echo "4. Verificando proceso de build..."
check "Copia archivos del proyecto" "COPY.*project/" "required"
check "Copia build.sbt" "COPY build.sbt" "required"
check "Copia código fuente (app)" "COPY app" "required"
check "Copia configuración (conf)" "COPY conf" "required"
check "Ejecuta sbt stage" "RUN.*sbt.*stage" "required"
echo ""

echo "5. Verificando copia de artifacts..."
check "Copia desde builder stage" "COPY --from=builder" "required"
check "Copia target/universal/stage" "target/universal/stage" "required"
echo ""

echo "6. Verificando configuración de producción..."
check "Expone puerto" "EXPOSE" "required"
check "Define JAVA_OPTS" "ENV JAVA_OPTS" "required"
check "JVM memoria mínima configurada" "Xms" "required"
check "JVM memoria máxima configurada" "Xmx" "required"
check "Usa G1GC" "UseG1GC" "optional"
echo ""

echo "7. Verificando manejo de variables de entorno..."
check "Usa variable PORT" 'PORT' "required"
check "Usa variable APPLICATION_SECRET" 'APPLICATION_SECRET' "required"
check "CMD definido" "CMD" "required"
check "Ejecuta /app/bin/web" "/app/bin/web" "required"
echo ""

echo "8. Verificando optimizaciones..."
check "Limpieza de apt cache" "apt-get clean" "optional"
check "Eliminación de listas apt" "rm -rf /var/lib/apt/lists" "optional"
echo ""

echo "========================================="
echo "Resumen:"
echo "========================================="

if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✓ Dockerfile válido - 0 errores${NC}"
else
    echo -e "${RED}✗ Se encontraron $ERRORS errores${NC}"
fi

if [ $WARNINGS -gt 0 ]; then
    echo -e "${YELLOW}⚠ $WARNINGS advertencias (no críticas)${NC}"
fi

echo ""
echo "Estructura del Dockerfile:"
echo "------------------------"
grep -n "^FROM\|^RUN\|^COPY\|^ENV\|^EXPOSE\|^CMD" "$DOCKERFILE" | head -20

echo ""
echo "========================================="
echo "Siguiente paso:"
echo "========================================="
echo "Para probar que funciona completamente:"
echo "1. docker build -t reactive-app ."
echo "2. docker run -p 9000:9000 -e PORT=9000 -e APPLICATION_SECRET=test-key-32-chars-minimum reactive-app"
echo ""
echo "Ver DOCKER_TEST_GUIDE.md para instrucciones completas"
echo ""

# Exit con código de error si hay errores
exit $ERRORS
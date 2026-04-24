#!/bin/bash

# Script de instalación de Java y SBT para Reactive-Manifiesto
# Compatible con Ubuntu/Debian

set -e  # Salir si hay algún error

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Función para imprimir mensajes
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Función para verificar si un comando existe
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Banner
echo "================================================"
echo "   Instalador de Dependencias"
echo "   Reactive-Manifiesto Project"
echo "================================================"
echo ""

# Verificar si se ejecuta como root
if [ "$EUID" -ne 0 ]; then 
    print_warning "Este script requiere permisos de superusuario"
    print_info "Ejecutando con sudo..."
    exec sudo "$0" "$@"
fi

# Actualizar repositorios
print_info "Actualizando repositorios del sistema..."
apt-get update -qq

# ============================================
# INSTALACIÓN DE JAVA
# ============================================
echo ""
print_info "=== Instalación de Java ==="

if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    print_warning "Java ya está instalado (versión $JAVA_VERSION)"
    
    # Verificar si la versión es compatible con Play Framework
    if [ "$JAVA_VERSION" = "11" ] || [ "$JAVA_VERSION" = "17" ] || [ "$JAVA_VERSION" = "21" ]; then
        print_success "La versión de Java es compatible con Play Framework"
        INSTALL_JAVA=false
    else
        print_warning "La versión de Java no es la recomendada para Play Framework (11, 17, 21)"
        read -p "¿Desea instalar OpenJDK 17? (s/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Ss]$ ]]; then
            INSTALL_JAVA=true
        else
            INSTALL_JAVA=false
        fi
    fi
else
    print_info "Java no está instalado. Instalando OpenJDK 17..."
    INSTALL_JAVA=true
fi

if [ "$INSTALL_JAVA" = true ]; then
    print_info "Instalando OpenJDK 17 LTS..."
    apt-get install -y openjdk-17-jdk openjdk-17-jre
    
    # Configurar JAVA_HOME
    JAVA_HOME_PATH=$(dirname $(dirname $(readlink -f $(which java))))
    
    if ! grep -q "JAVA_HOME" /etc/environment; then
        echo "JAVA_HOME=\"$JAVA_HOME_PATH\"" >> /etc/environment
        print_success "JAVA_HOME configurado en /etc/environment"
    fi
    
    export JAVA_HOME="$JAVA_HOME_PATH"
    
    # Verificar instalación
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1)
        print_success "Java instalado correctamente: $JAVA_VERSION"
    else
        print_error "Error al instalar Java"
        exit 1
    fi
else
    print_info "Omitiendo instalación de Java"
fi

# ============================================
# INSTALACIÓN DE SBT
# ============================================
echo ""
print_info "=== Instalación de SBT ==="

if command_exists sbt; then
    SBT_VERSION=$(sbt --version 2>&1 | grep "sbt runner version" | awk '{print $4}')
    print_warning "SBT ya está instalado (versión $SBT_VERSION)"
    read -p "¿Desea reinstalar SBT? (s/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Ss]$ ]]; then
        print_info "Omitiendo instalación de SBT"
        INSTALL_SBT=false
    else
        INSTALL_SBT=true
    fi
else
    print_info "SBT no está instalado. Instalando..."
    INSTALL_SBT=true
fi

if [ "$INSTALL_SBT" = true ]; then
    # Instalar dependencias necesarias
    print_info "Instalando dependencias necesarias..."
    apt-get install -y curl gnupg apt-transport-https
    
    # Descargar e instalar la clave GPG de SBT
    print_info "Configurando repositorio de SBT..."
    
    # Método 1: Intentar desde keyserver de Ubuntu
    if curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --dearmor -o /usr/share/keyrings/sbt-archive-keyring.gpg 2>/dev/null; then
        print_success "Clave GPG descargada correctamente"
    else
        print_warning "No se pudo descargar la clave desde keyserver, intentando método alternativo..."
        
        # Método 2: Crear clave manualmente (fallback)
        apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 2>/dev/null || true
    fi
    
    # Agregar repositorio de SBT
    echo "deb [signed-by=/usr/share/keyrings/sbt-archive-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/sbt-archive-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list > /dev/null
    
    # Actualizar repositorios e instalar SBT
    print_info "Actualizando repositorios..."
    apt-get update -qq
    
    print_info "Instalando SBT..."
    apt-get install -y sbt
    
    # Verificar instalación
    if command_exists sbt; then
        SBT_VERSION=$(sbt --version 2>&1 | grep "sbt runner version" | awk '{print $4}')
        print_success "SBT instalado correctamente: versión $SBT_VERSION"
    else
        print_error "Error al instalar SBT"
        exit 1
    fi
fi

# ============================================
# VERIFICACIÓN FINAL
# ============================================
echo ""
print_info "=== Verificación de Instalación ==="
echo ""

# Verificar Java
if command_exists java; then
    JAVA_INFO=$(java -version 2>&1 | head -n 1)
    print_success "Java: $JAVA_INFO"
    if [ -n "$JAVA_HOME" ]; then
        print_success "JAVA_HOME: $JAVA_HOME"
    fi
else
    print_error "Java no está disponible"
fi

# Verificar SBT
if command_exists sbt; then
    # Ejecutar sbt --version de forma silenciosa
    SBT_INFO=$(sbt --version 2>&1 | grep "sbt version" | head -n 1)
    print_success "SBT: $SBT_INFO"
else
    print_error "SBT no está disponible"
fi

# Verificar Scala (instalado automáticamente con SBT)
if command_exists scala; then
    SCALA_INFO=$(scala -version 2>&1)
    print_success "Scala: $SCALA_INFO"
fi

echo ""
print_success "================================================"
print_success "  Instalación completada exitosamente"
print_success "================================================"
echo ""

# Información sobre el proyecto
if [ -f "build.sbt" ]; then
    print_info "Proyecto detectado en el directorio actual"
    print_info "Comandos útiles:"
    echo "  - sbt compile  : Compilar el proyecto"
    echo "  - sbt run      : Ejecutar la aplicación"
    echo "  - sbt test     : Ejecutar tests"
    echo "  - sbt clean    : Limpiar archivos compilados"
    echo ""
    
    read -p "¿Desea compilar el proyecto ahora? (s/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Ss]$ ]]; then
        print_info "Compilando proyecto..."
        su -c "sbt compile" - ${SUDO_USER:-$USER}
    fi
fi

print_info "Para aplicar JAVA_HOME en la sesión actual, ejecute:"
echo "  source /etc/environment"

#!/bin/bash

# Script de instalación rápida de Java y SBT
# Sin confirmaciones interactivas

set -e

echo "🚀 Instalación rápida de dependencias para Reactive-Manifiesto"
echo ""

# Actualizar repositorios
echo "📦 Actualizando repositorios..."
sudo apt-get update -qq

# Instalar Java 17 si no está instalado
if ! command -v java &> /dev/null; then
    echo "☕ Instalando OpenJDK 17..."
    sudo apt-get install -y openjdk-17-jdk openjdk-17-jre
    echo "✅ Java instalado"
else
    echo "✅ Java ya está instalado: $(java -version 2>&1 | head -n 1)"
fi

# Instalar SBT si no está instalado
if ! command -v sbt &> /dev/null; then
    echo "🔧 Instalando SBT..."
    
    # Instalar dependencias
    sudo apt-get install -y curl gnupg apt-transport-https
    
    # Configurar repositorio SBT
    curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo gpg --dearmor -o /usr/share/keyrings/sbt-archive-keyring.gpg
    echo "deb [signed-by=/usr/share/keyrings/sbt-archive-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
    
    # Instalar SBT
    sudo apt-get update -qq
    sudo apt-get install -y sbt
    echo "✅ SBT instalado"
else
    echo "✅ SBT ya está instalado: $(sbt --version 2>&1 | grep 'sbt runner' | awk '{print $4}')"
fi

echo ""
echo "✅ Instalación completada"
echo ""
echo "📝 Comandos disponibles:"
echo "   sbt compile - Compilar el proyecto"
echo "   sbt run     - Ejecutar la aplicación"
echo "   sbt test    - Ejecutar tests"

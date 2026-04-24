#!/bin/bash

# Script para monitorear códigos de verificación en modo desarrollo
# Filtra los logs de sbt run para mostrar solo los códigos de verificación

echo "🔍 Monitoreando códigos de verificación..."
echo "📝 Este script muestra los códigos que se generan cuando un usuario intenta hacer login sin verificar"
echo "----------------------------------------"
echo ""

# Buscar proceso de Play/sbt
if pgrep -f "sbt run" > /dev/null; then
    echo "✅ Servidor detectado corriendo"
    echo "📧 Esperando códigos de verificación..."
    echo ""
    
    # Monitorear logs (ajustar ruta según tu configuración)
    tail -f target/logs/application.log 2>/dev/null | grep -A 6 "CÓDIGO DE VERIFICACIÓN" || \
    tail -f logs/application.log 2>/dev/null | grep -A 6 "CÓDIGO DE VERIFICACIÓN" || \
    echo "⚠️  No se pueden encontrar logs. Los códigos aparecerán en la terminal donde ejecutaste 'sbt run'"
else
    echo "❌ No se detectó servidor corriendo"
    echo ""
    echo "Para iniciar el servidor, ejecuta:"
    echo "  sbt run"
    echo ""
    echo "Luego ejecuta este script en otra terminal"
fi

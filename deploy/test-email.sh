#!/bin/bash

# Script para probar el sistema de verificación por email

echo "🧪 Test de Sistema de Verificación de Email"
echo "==========================================="
echo ""

# Verificar modo actual
EMAIL_ENABLED=$(grep "email.enabled" conf/application.conf | grep -v "#" | head -1 | cut -d'=' -f2 | tr -d ' ')

if [ "$EMAIL_ENABLED" == "false" ] || [ -z "$EMAIL_ENABLED" ]; then
    echo "📝 Modo: DESARROLLO (emails solo en consola)"
    echo ""
    echo "Los códigos de verificación aparecerán en los logs cuando:"
    echo "  1. Un usuario nuevo se registre"
    echo "  2. Un usuario existente sin verificar intente iniciar sesión"
    echo ""
    echo "Busca en la consola mensajes como:"
    echo "  ========================================"
    echo "   📧 CÓDIGO DE VERIFICACIÓN (DEV MODE)"
    echo "  ========================================"
    echo "   Email: usuario@example.com"
    echo "   Código: 456"
    echo "   Expira en: 5 minutos"
    echo "  ========================================"
    echo ""
    echo "Para habilitar envío REAL de emails:"
    echo "  1. Edita conf/application.conf"
    echo "  2. Cambia 'email.enabled = false' a 'email.enabled = true'"
    echo "  3. Configura las credenciales SMTP (ver EMAIL_CONFIGURATION.md)"
else
    echo "📧 Modo: PRODUCCIÓN (envío real de emails)"
    echo ""
    
    # Verificar variables de entorno
    if [ -z "$EMAIL_USER" ] || [ -z "$EMAIL_PASSWORD" ]; then
        echo "⚠️  ADVERTENCIA: Variables de entorno no configuradas"
        echo ""
        echo "Necesitas configurar:"
        echo "  export EMAIL_USER='tu-email@gmail.com'"
        echo "  export EMAIL_PASSWORD='xxxx-xxxx-xxxx-xxxx'"
        echo ""
        echo "Para Gmail, genera una contraseña de aplicación en:"
        echo "  https://myaccount.google.com/apppasswords"
        echo ""
    else
        echo "✅ EMAIL_USER configurado: $EMAIL_USER"
        echo "✅ EMAIL_PASSWORD configurado: ****"
        echo ""
        echo "Los emails se enviarán realmente cuando:"
        echo "  1. Un usuario nuevo se registre"
        echo "  2. Un usuario existente sin verificar intente iniciar sesión"
        echo ""
    fi
fi

echo ""
echo "📚 Para más información, consulta: resource/EMAIL_CONFIGURATION.md"
echo ""
echo "🚀 Iniciando aplicación..."
echo ""

cd "$(dirname "$0")"
sbt run

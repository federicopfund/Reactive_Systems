#!/bin/bash

echo "🔍 Verificando compilación de SASS..."
echo ""

# 1. Verificar archivos SCSS
echo "📁 Archivos SCSS fuente:"
ls -lh app/assets/stylesheets/*.scss 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
ls -lh app/assets/stylesheets/components/*.scss 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
echo ""

# 2. Verificar CSS compilado
echo "📦 CSS compilado por SASS:"
if [ -f "target/web/sass/main/stylesheets/main.css" ]; then
    SIZE=$(ls -lh target/web/sass/main/stylesheets/main.css | awk '{print $5}')
    echo "  ✅ target/web/sass/main/stylesheets/main.css ($SIZE)"
    echo ""
    echo "  Primeros 200 caracteres:"
    head -c 200 target/web/sass/main/stylesheets/main.css
    echo ""
    echo "..."
else
    echo "  ❌ CSS no encontrado"
fi
echo ""

# 3. Verificar servidor
echo "🚀 Estado del servidor:"
if pgrep -f "sbt.*run" > /dev/null; then
    echo "  ✅ Servidor corriendo"
    PID=$(pgrep -f "sbt.*run")
    echo "  PID: $PID"
else
    echo "  ❌ Servidor no está corriendo"
    echo "  💡 Ejecuta: sbt run"
fi
echo ""

# 4. Verificar endpoint
echo "🌐 Verificando endpoint HTTP:"
if pgrep -f "sbt.*run" > /dev/null; then
    sleep 2
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/assets/stylesheets/main.css 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        echo "  ✅ http://localhost:9000/assets/stylesheets/main.css (HTTP $HTTP_CODE)"
        echo ""
        echo "  Primeros 150 caracteres del CSS servido:"
        curl -s http://localhost:9000/assets/stylesheets/main.css 2>/dev/null | head -c 150
        echo ""
        echo "..."
    elif [ "$HTTP_CODE" = "000" ]; then
        echo "  ⏳ Servidor iniciando... (reintenta en 10 segundos)"
    else
        echo "  ❌ Error HTTP $HTTP_CODE"
    fi
else
    echo "  ⏭️  Servidor no está corriendo"
fi
echo ""

# 5. Resumen
echo "📋 Resumen:"
echo "  • Archivos SCSS: $(ls -1 app/assets/stylesheets/*.scss app/assets/stylesheets/components/*.scss 2>/dev/null | wc -l) archivos"
echo "  • CSS compilado: $([ -f target/web/sass/main/stylesheets/main.css ] && echo "✅ Sí" || echo "❌ No")"
echo "  • Servidor: $(pgrep -f "sbt.*run" > /dev/null && echo "✅ Running" || echo "❌ Stopped")"
echo "  • Endpoint: $(curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/assets/stylesheets/main.css 2>/dev/null | grep -q "200" && echo "✅ Accesible" || echo "⏳ No disponible")"
echo ""

echo "🎨 Para ver la aplicación abre: http://localhost:9000"

#!/usr/bin/env bash
# domain-folder-init.sh — Crea la estructura de carpetas para un nuevo dominio.
# Uso: bash domain-folder-init.sh <nombre-dominio>
# Ejemplo: bash domain-folder-init.sh reviews

set -euo pipefail

DOMAIN="${1:?Uso: $0 <nombre-dominio>}"
BASE="app/domains/${DOMAIN}"

mkdir -p "${BASE}/models"
mkdir -p "${BASE}/repositories"
mkdir -p "${BASE}/engines"
mkdir -p "${BASE}/services"
mkdir -p "${BASE}/policies"

# Archivos placeholder para que git los trackee
for dir in models repositories engines services policies; do
  touch "${BASE}/${dir}/.gitkeep"
done

echo "✓ Estructura creada en ${BASE}/"
echo ""
echo "Próximos pasos:"
echo "  1. Crear modelos en ${BASE}/models/"
echo "  2. Crear repository en ${BASE}/repositories/"
echo "  3. Crear engine en ${BASE}/engines/"
echo "  4. Crear adapter en ${BASE}/services/"
echo "  5. Registrar en app/infrastructure/guardian/DomainGuardian.scala"
echo "  6. Enlazar en app/Module.scala"
echo "  7. Agregar rutas en conf/routes"
echo "  8. Crear evolución SQL en conf/evolutions/default/<N>.sql"

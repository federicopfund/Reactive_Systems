#!/bin/bash
# ─────────────────────────────────────────────────────────
#  run.sh — Arranca la app Play y muestra el link del navegador
# ─────────────────────────────────────────────────────────

PORT=${1:-9000}

if [ -n "$CODESPACE_NAME" ] && [ -n "$GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN" ]; then
  URL="https://${CODESPACE_NAME}-${PORT}.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}"
else
  URL="http://localhost:${PORT}"
fi

echo ""
echo "┌─────────────────────────────────────────────────────┐"
echo "│  Reactive Systems — Play Framework                  │"
echo "│                                                     │"
echo "│  URL: $URL"
echo "│                                                     │"
echo "│  Esperando que el servidor inicie...                │"
echo "└─────────────────────────────────────────────────────┘"
echo ""

exec sbt "run $PORT"
## Para activa Code Enginner y escalar a una instancia 
## ibmcloud ce application update --name reactive-systems-app --min-scale 0 --max-scale 1
#!/usr/bin/env bash
# Commits each changed file individually with an auto-generated message.
# Usage: bash commit-each.sh
set -e

cd "$(git rev-parse --show-toplevel)"

commit_msg() {
  local status="$1"   # D, M, or N
  local file="$2"
  local base
  base="$(basename "$file")"
  local dir
  dir="$(dirname "$file")"

  local scope=""
  case "$dir" in
    app/views/admin*)   scope="views/admin" ;;
    app/views/auth*)    scope="views/auth" ;;
    app/views/user*)    scope="views/user" ;;
    app/views/events*)  scope="views/events" ;;
    app/views/collections*) scope="views/collections" ;;
    app/views/publications*) scope="views/publications" ;;
    app/views/seasons*) scope="views/seasons" ;;
    app/views/partials*) scope="views/partials" ;;
    app/views/errors*)  scope="views/errors" ;;
    app/views/legal*)   scope="views/legal" ;;
    app/views*)         scope="views" ;;
    app/controllers/admin*) scope="controllers/admin" ;;
    app/controllers/auth*)  scope="controllers/auth" ;;
    app/controllers/user*)  scope="controllers/user" ;;
    app/controllers/web*)   scope="controllers/web" ;;
    app/controllers*)   scope="controllers" ;;
    app/domains/admin*) scope="domains/admin" ;;
    app/domains/collections*) scope="domains/collections" ;;
    app/domains/contact*) scope="domains/contact" ;;
    app/domains/editorial*) scope="domains/editorial" ;;
    app/domains/events*) scope="domains/events" ;;
    app/domains/gamification*) scope="domains/gamification" ;;
    app/domains/identity*) scope="domains/identity" ;;
    app/domains/messaging*) scope="domains/messaging" ;;
    app/domains/newsletter*) scope="domains/newsletter" ;;
    app/domains/publications*) scope="domains/publications" ;;
    app/domains*)       scope="domains" ;;
    app/infrastructure/guardian*) scope="infrastructure/guardian" ;;
    app/infrastructure*) scope="infrastructure" ;;
    app/shared*)        scope="shared" ;;
    app/assets/stylesheets/admin*) scope="styles/admin" ;;
    app/assets/stylesheets/auth*) scope="styles/auth" ;;
    app/assets/stylesheets/user*) scope="styles/user" ;;
    app/assets/stylesheets/events*) scope="styles/events" ;;
    app/assets/stylesheets/shared*) scope="styles/shared" ;;
    app/assets/stylesheets/tags*) scope="styles/tags" ;;
    app/assets/stylesheets/collections*) scope="styles/collections" ;;
    app/assets/stylesheets/publications*) scope="styles/publications" ;;
    app/assets/stylesheets/seasons*) scope="styles/seasons" ;;
    app/assets/stylesheets/sections*) scope="styles/sections" ;;
    app/assets/stylesheets/editorial*) scope="styles/editorial" ;;
    app/assets/stylesheets/legal*) scope="styles/legal" ;;
    app/assets/stylesheets*) scope="styles" ;;
    app/models*)        scope="models" ;;
    app/repositories*)  scope="repositories" ;;
    app/services*)      scope="services" ;;
    app/utils*)         scope="utils" ;;
    app/core/guardian*) scope="guardian" ;;
    app/core*)          scope="core" ;;
    test/controllers*)  scope="test/controllers" ;;
    test/core*)         scope="test/core" ;;
    test/services*)     scope="test/services" ;;
    test/utils*)        scope="test/utils" ;;
    conf*)              scope="config" ;;
    *)                  scope="" ;;
  esac

  local name="${base%.*}"   # strip extension for readability

  case "$status" in
    D)
      if [[ "$dir" == app/models* || "$dir" == app/repositories* || "$dir" == app/services* || "$dir" == app/core* || "$dir" == app/controllers* || "$dir" == app/utils* ]]; then
        echo "refactor(${scope}): move ${name} to domain-driven structure"
      else
        echo "chore(${scope}): remove ${name}"
      fi
      ;;
    M)
      case "$dir" in
        test*) echo "test(${scope}): update ${name}" ;;
        conf*)  echo "chore(${scope}): update ${name}" ;;
        app/assets*) echo "style(${scope}): update ${name}" ;;
        app/views*) echo "refactor(${scope}): update ${name} template" ;;
        app/controllers*) echo "refactor(${scope}): update ${name}" ;;
        *) echo "refactor(${scope}): update ${name}" ;;
      esac
      ;;
    N)
      case "$dir" in
        app/assets/stylesheets*) echo "style(${scope}): add ${name}" ;;
        app/domains*) echo "feat(${scope}): add ${name} domain component" ;;
        app/infrastructure*) echo "feat(${scope}): add ${name}" ;;
        app/shared*) echo "feat(${scope}): add ${name} shared component" ;;
        app/controllers*) echo "feat(${scope}): add ${name}" ;;
        app/views*) echo "feat(${scope}): add ${name} view" ;;
        *) echo "feat(${scope}): add ${name}" ;;
      esac
      ;;
  esac
}

echo "=== FASE 1: Eliminados ==="
while IFS= read -r file; do
  msg="$(commit_msg D "$file")"
  echo "  DEL  $file → \"$msg\""
  git rm -- "$file"
  git commit -m "$msg"
done < <(git ls-files --deleted)

echo ""
echo "=== FASE 2: Modificados ==="
while IFS= read -r file; do
  msg="$(commit_msg M "$file")"
  echo "  MOD  $file → \"$msg\""
  git add -- "$file"
  git commit -m "$msg"
done < <(git ls-files --modified)

echo ""
echo "=== FASE 3: Nuevos/sin seguimiento ==="
while IFS= read -r file; do
  msg="$(commit_msg N "$file")"
  echo "  NEW  $file → \"$msg\""
  git add -- "$file"
  git commit -m "$msg"
done < <(git ls-files --others --exclude-standard)

echo ""
echo "Listo. Commits generados: $(git log --oneline HEAD~"$(git rev-list --count HEAD)" 2>/dev/null | wc -l || true)"

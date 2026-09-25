#!/usr/bin/env bash
# Executa a coleção Postman com o Newman (testes E2E dos endpoints).
#   ./scripts/run-postman.sh                      # ambiente local (http://localhost:8080)
#   POSTMAN_ENV=hml ./scripts/run-postman.sh      # ambiente de homologação na AWS
#   BASE_URL=https://xyz.cloudfront.net ./scripts/run-postman.sh
#   ./scripts/run-postman.sh --folder "00 - Plataforma"   # argumentos extras vão para o newman
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POSTMAN_ENV="${POSTMAN_ENV:-local}"
COLLECTION="postman/vagaviva-api.postman_collection.json"
ENVIRONMENT="postman/vagaviva-${POSTMAN_ENV}.postman_environment.json"
REPORT_DIR="target/newman"
NEWMAN_VERSION="${NEWMAN_VERSION:-6.1.3}"

cd "$ROOT_DIR"
[[ -f "$COLLECTION" ]] || { echo "Coleção não encontrada: $COLLECTION" >&2; exit 1; }
[[ -f "$ENVIRONMENT" ]] || { echo "Ambiente não encontrado: $ENVIRONMENT" >&2; exit 1; }
mkdir -p "$REPORT_DIR"

ARGS=(run "$COLLECTION" -e "$ENVIRONMENT"
      --reporters cli,junit --reporter-junit-export "$REPORT_DIR/newman-${POSTMAN_ENV}.xml"
      --color on)
if [[ -n "${BASE_URL:-}" ]]; then
  echo "→ BASE_URL override: $BASE_URL"
  ARGS+=(--env-var "baseUrl=$BASE_URL")
fi
ARGS+=("$@")

if [[ "${NEWMAN_RUNNER:-auto}" != "docker" ]] && command -v npx >/dev/null 2>&1; then
  echo "→ Newman $NEWMAN_VERSION via npx (ambiente: $POSTMAN_ENV)"
  exec npx --yes "newman@${NEWMAN_VERSION}" "${ARGS[@]}"
fi

# Fallback: Newman em container, na mesma rede do docker-compose quando o app estiver rodando.
NETWORK_ARG=()
APP_CONTAINER="${APP_CONTAINER_NAME:-vagaviva-app}"
if [[ -z "${BASE_URL:-}" ]] && docker inspect "$APP_CONTAINER" >/dev/null 2>&1; then
  NETWORK_NAME="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{println $k}}{{end}}' "$APP_CONTAINER" | head -n1 | tr -d '[:space:]')"
  NETWORK_ARG=(--network "$NETWORK_NAME")
  ARGS+=(--env-var "baseUrl=http://$APP_CONTAINER:8080")
fi
echo "→ Newman $NEWMAN_VERSION via Docker (ambiente: $POSTMAN_ENV)"
exec docker run --rm ${NETWORK_ARG+"${NETWORK_ARG[@]}"} -v "$ROOT_DIR:/etc/newman" -w /etc/newman \
  "postman/newman:${NEWMAN_VERSION}-alpine" "${ARGS[@]}"

#!/usr/bin/env bash
# Publica a imagem do commit no ECR e aplica no demo (liga a instância se preciso) — o mesmo
# caminho do CD (scripts/aws/demo-deploy.sh), para uso manual.
# Uso: ./scripts/aws/demo-refresh.sh <tag>   (padrão: HEAD do git)
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
TAG="${1:-$(git -C "$ROOT_DIR" rev-parse HEAD)}"

"$ROOT_DIR/scripts/aws/push-image.sh" "$TAG"
"$ROOT_DIR/scripts/aws/demo-deploy.sh" "$TAG"

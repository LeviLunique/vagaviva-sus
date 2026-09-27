#!/usr/bin/env bash
# Build (linux/arm64) e push da imagem da API para o ECR.   Uso: ./scripts/aws/push-image.sh <tag>
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
TAG="${1:?informe a tag da imagem}"
IMAGE="$ECR_REGISTRY/$ECR_REPOSITORY:$TAG"

if aws ecr describe-images --repository-name "$ECR_REPOSITORY" --image-ids imageTag="$TAG" >/dev/null 2>&1; then
  log "Imagem $IMAGE já existe no ECR"; exit 0
fi
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"
log "Build e push de $IMAGE"
docker buildx build --platform linux/arm64 --provenance=false -t "$IMAGE" --push "$ROOT_DIR"

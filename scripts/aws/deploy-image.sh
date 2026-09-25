#!/usr/bin/env bash
# Implanta uma imagem já publicada no ECR em um ambiente (usado pelo pipeline de deploy).
# Registra nova revisão da task definition a partir da última revisão (mantendo a config do
# Terraform), atualiza o serviço e aguarda estabilizar. O circuit breaker do ECS faz rollback.
# Uso: ./scripts/aws/deploy-image.sh <hml|prod> <tag>
set -euo pipefail
ENV="${1:?ambiente}"; TAG="${2:?tag}"
PREFIX="vagaviva-$ENV"
REGION="${AWS_REGION:-sa-east-1}"

repo_uri="$(aws ecr describe-repositories --repository-names vagaviva-api --query 'repositories[0].repositoryUri' --output text)"
current="$(aws ecs describe-task-definition --task-definition "$PREFIX-api" --query taskDefinition --output json)"
next="$(jq --arg img "$repo_uri:$TAG" '
  .containerDefinitions |= map(if .name == "api" then .image = $img else . end)
  | {family, taskRoleArn, executionRoleArn, networkMode, containerDefinitions, volumes,
     placementConstraints, requiresCompatibilities, cpu, memory, runtimePlatform}
  | with_entries(select(.value != null))' <<<"$current")"

arn="$(aws ecs register-task-definition --cli-input-json "$next" \
  --tags key=Project,value=vagaviva key=Environment,value="$ENV" \
  --query 'taskDefinition.taskDefinitionArn' --output text)"
echo "→ Nova revisão: $arn"

aws ssm put-parameter --name "/vagaviva/$ENV/api/image-tag" --value "$TAG" --type String --overwrite >/dev/null
aws ecs update-service --cluster "$PREFIX" --service "$PREFIX-api" --task-definition "$arn" >/dev/null
echo "→ Aguardando o serviço $PREFIX-api estabilizar..."
aws ecs wait services-stable --cluster "$PREFIX" --services "$PREFIX-api"
echo "✔ $ENV executando $TAG"

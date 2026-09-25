#!/usr/bin/env bash
# Retoma um ambiente pausado.   Uso: ./scripts/aws/resume.sh hml
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
ENV="${1:?ambiente}"; P="$PROJECT-$ENV"
aws rds start-db-instance --db-instance-identifier "$P" >/dev/null 2>&1 || true
log "Aguardando o PostgreSQL ficar disponível..."
aws rds wait db-instance-available --db-instance-identifier "$P"
MIN="$(grep -E '^app_min_count' "$ROOT_DIR/infra/envs/$ENV/terraform.tfvars" | awk -F= '{gsub(/ /,"",$2); print $2}')"
MAX="$(grep -E '^app_max_count' "$ROOT_DIR/infra/envs/$ENV/terraform.tfvars" | awk -F= '{gsub(/ /,"",$2); print $2}')"
aws application-autoscaling register-scalable-target --service-namespace ecs \
  --resource-id "service/$P/$P-api" --scalable-dimension ecs:service:DesiredCount --min-capacity "$MIN" --max-capacity "$MAX"
aws ecs update-service --cluster "$P" --service "$P-api" --desired-count "$MIN" >/dev/null
aws ecs wait services-stable --cluster "$P" --services "$P-api"
log "$ENV retomado"

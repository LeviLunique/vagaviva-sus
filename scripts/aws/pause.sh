#!/usr/bin/env bash
# Pausa um ambiente para economizar (tasks = 0 e RDS parado). Uso: ./scripts/aws/pause.sh hml
# Observação: a AWS religa automaticamente um RDS parado após 7 dias.
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
ENV="${1:?ambiente}"; P="$PROJECT-$ENV"
aws application-autoscaling register-scalable-target --service-namespace ecs \
  --resource-id "service/$P/$P-api" --scalable-dimension ecs:service:DesiredCount --min-capacity 0 --max-capacity 0
aws ecs update-service --cluster "$P" --service "$P-api" --desired-count 0 >/dev/null
aws rds stop-db-instance --db-instance-identifier "$P" >/dev/null || true
log "$ENV pausado (ALB, CloudFront e WAF continuam ativos; custo residual baixo)"

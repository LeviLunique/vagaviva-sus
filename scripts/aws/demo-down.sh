#!/usr/bin/env bash
# Desliga manualmente a instância do perfil demo (o desligamento automático por ociosidade
# roda sozinho a cada poucos minutos; use este script para economizar imediatamente).
# Uso: ./scripts/aws/demo-down.sh
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
STACK="$ROOT_DIR/infra/envs/demo"
tf_init "$STACK" "envs/demo/terraform.tfstate" >/dev/null
INSTANCE_ID="$(terraform -chdir="$STACK" output -raw instance_id)"
aws ec2 stop-instances --instance-ids "$INSTANCE_ID" >/dev/null
log "Desligando $INSTANCE_ID (religa sozinha no próximo acesso, ou com ./scripts/aws/demo-up.sh)"

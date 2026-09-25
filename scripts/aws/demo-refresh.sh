#!/usr/bin/env bash
# Publica uma nova imagem no perfil demo e, se a instância já estiver ligada, força a
# atualização imediata via SSM Run Command (sem precisar desligar/religar).
# Uso: ./scripts/aws/demo-refresh.sh <tag>   (padrão: HEAD do git)
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
STACK="$ROOT_DIR/infra/envs/demo"
TAG="${1:-$(git -C "$ROOT_DIR" rev-parse HEAD)}"

"$ROOT_DIR/scripts/aws/push-image.sh" "$TAG"
aws ssm put-parameter --name "/$PROJECT/demo/api/image-tag" --value "$TAG" --type String --overwrite >/dev/null
log "Tag $TAG publicada em /$PROJECT/demo/api/image-tag"

tf_init "$STACK" "envs/demo/terraform.tfstate" >/dev/null
INSTANCE_ID="$(terraform -chdir="$STACK" output -raw instance_id)"
STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)"

if [[ "$STATE" != "running" ]]; then
  log "Instância desligada: a nova imagem entra sozinha no próximo acesso/religamento."
  exit 0
fi

log "Instância ligada: aplicando agora via SSM Run Command"
CMD_ID="$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
  --parameters 'commands=["/opt/vagaviva/refresh.sh"]' --query Command.CommandId --output text)"
aws ssm wait command-executed --command-id "$CMD_ID" --instance-id "$INSTANCE_ID"
aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query '{status:Status,stdout:StandardOutputContent,stderr:StandardErrorContent}' --output json

#!/usr/bin/env bash
# Aplica no ambiente de demonstração uma imagem já publicada no ECR (usado pelo CD e manualmente):
# grava a tag no SSM, liga a instância se estiver parada, roda o /opt/vagaviva/refresh.sh via SSM
# Run Command e espera a API responder UP pela CloudFront.
# Uso: ./scripts/aws/demo-deploy.sh <tag>
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
TAG="${1:?informe a tag da imagem (SHA do commit)}"
ENV="demo"

aws ssm put-parameter --name "/$PROJECT/$ENV/api/image-tag" --value "$TAG" --type String --overwrite >/dev/null
log "Tag $TAG gravada em /$PROJECT/$ENV/api/image-tag"

INSTANCE_ID="$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=$PROJECT-$ENV" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)"
[[ "$INSTANCE_ID" != "None" ]] || { echo "Instância $PROJECT-$ENV não encontrada." >&2; exit 1; }
STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)"
if [[ "$STATE" == "stopping" ]]; then
  aws ec2 wait instance-stopped --instance-ids "$INSTANCE_ID"
  STATE="stopped"
fi
if [[ "$STATE" == "stopped" ]]; then
  log "Ligando $INSTANCE_ID (o boot já sobe a nova tag)"
  aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
  aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
fi

log "Aguardando o agente do SSM"
for _ in $(seq 1 60); do
  PING="$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$INSTANCE_ID" \
    --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)"
  [[ "$PING" == "Online" ]] && break
  sleep 5
done
[[ "$PING" == "Online" ]] || { echo "Agente do SSM não ficou online." >&2; exit 1; }

# Depois de um boot, espera o serviço de inicialização terminar (ele também roda o refresh.sh) para
# os dois não disputarem os contêineres; numa instância já ligada, o serviço já está ativo.
log "Aplicando a imagem via SSM Run Command"
CMD_ID="$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
  --timeout-seconds 900 --comment "deploy $TAG" \
  --parameters 'commands=["for i in $(seq 1 60); do systemctl is-active --quiet vagaviva-demo.service && break; sleep 5; done","/opt/vagaviva/refresh.sh","docker inspect vagaviva-demo-app --format {{.Config.Image}}"]' \
  --query Command.CommandId --output text)"
for _ in $(seq 1 90); do
  STATUS="$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || true)"
  case "$STATUS" in Success|Failed|TimedOut|Cancelled) break ;; esac
  sleep 10
done
aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query '[StandardOutputContent,StandardErrorContent]' --output text | tail -5
[[ "$STATUS" == "Success" ]] || { echo "refresh.sh terminou com $STATUS" >&2; exit 1; }

URL="$(aws ssm get-parameter --name "/$PROJECT/$ENV/api/public-base-url" --query Parameter.Value --output text)"
log "Aguardando $URL/actuator/health responder UP"
for _ in $(seq 1 40); do
  if curl -fsS -m 10 "$URL/actuator/health" | grep -q '"status":"UP"'; then
    log "✔ $URL no ar com a imagem $TAG"
    exit 0
  fi
  sleep 10
done
echo "A API não respondeu UP a tempo — veja ./scripts/aws/demo-logs.sh" >&2
exit 1

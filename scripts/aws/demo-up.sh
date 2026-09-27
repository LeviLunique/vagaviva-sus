#!/usr/bin/env bash
# Liga manualmente a instância do perfil demo (útil para preparar uma gravação com antecedência,
# sem depender do fluxo automático de "acordar no primeiro acesso"). Uso: ./scripts/aws/demo-up.sh
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
STACK="$ROOT_DIR/infra/envs/demo"
tf_init "$STACK" "envs/demo/terraform.tfstate" >/dev/null
INSTANCE_ID="$(terraform -chdir="$STACK" output -raw instance_id)"

STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)"
if [[ "$STATE" == "running" ]]; then
  log "Instância já está ligada ($INSTANCE_ID)"
else
  log "Ligando $INSTANCE_ID (estado atual: $STATE)"
  aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
  aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
fi

URL="$(terraform -chdir="$STACK" output -raw api_base_url)"
log "Aguardando a aplicação responder em $URL (pode levar ~60-90s após o boot)"
# /actuator/health é uma rota "de entrada" (vai pelo origin group com religamento automático);
# o restante da API (POST/PUT/PATCH/DELETE) vai direto para a EC2, sem esse religamento — por
# isso sempre confira esta rota antes de iniciar qualquer teste/gravação.
for i in $(seq 1 30); do
  if curl -fsS -m 5 "$URL/actuator/health" | grep -q '"UP"'; then
    log "✔ $URL está no ar"
    exit 0
  fi
  sleep 10
done
echo "A aplicação não respondeu a tempo. Verifique os logs: ./scripts/aws/demo-logs.sh" >&2
exit 1

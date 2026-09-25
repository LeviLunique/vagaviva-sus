#!/usr/bin/env bash
# Terraform de um ambiente.   Uso: ./scripts/aws/infra.sh <hml|prod> <plan|apply|destroy|output>
# No primeiro apply, publica a imagem do commit atual no ECR (o serviço ECS precisa de uma imagem).
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
ENV="${1:?informe o ambiente: hml|prod}"
ACTION="${2:-plan}"
STACK="$ROOT_DIR/infra/envs/$ENV"
[[ -d "$STACK" ]] || { echo "Ambiente inválido: $ENV" >&2; exit 1; }

tf_init "$STACK" "envs/$ENV/terraform.tfstate"

EXTRA=()
if [[ "$ACTION" == "apply" ]] && ! aws ssm get-parameter --name "/$PROJECT/$ENV/api/image-tag" >/dev/null 2>&1; then
  TAG="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  log "Primeiro deploy de $ENV: publicando imagem $TAG"
  "$ROOT_DIR/scripts/aws/push-image.sh" "$TAG"
  EXTRA+=(-var "initial_image_tag=$TAG")
fi

case "$ACTION" in
  plan)    terraform -chdir="$STACK" plan -input=false ${EXTRA+"${EXTRA[@]}"} ;;
  apply)   terraform -chdir="$STACK" apply -input=false -auto-approve ${EXTRA+"${EXTRA[@]}"}
           url="$(terraform -chdir="$STACK" output -raw api_base_url)"
           log "Registrando APP_BASE_URL=$url no environment $ENV do GitHub"
           gh variable set APP_BASE_URL --env "$ENV" --repo "$GITHUB_REPO" --body "$url"
           [[ "$ENV" == "hml" ]] && gh variable set AWS_DEPLOY_ENABLED --repo "$GITHUB_REPO" --body true
           terraform -chdir="$STACK" output ;;
  destroy) terraform -chdir="$STACK" destroy -input=false ;;
  output)  terraform -chdir="$STACK" output ;;
  *) echo "Ação inválida: $ACTION" >&2; exit 1 ;;
esac

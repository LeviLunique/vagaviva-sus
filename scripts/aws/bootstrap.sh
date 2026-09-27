#!/usr/bin/env bash
# Bootstrap único da conta AWS + configuração do GitHub (environments e variáveis).
# Cria: bucket de estado do Terraform, ECR, role OIDC de deploy do demo (ADR-0014) e orçamento mensal.
# Uso: TF_VAR_budget_alert_email=voce@exemplo.com ./scripts/aws/bootstrap.sh
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
STACK="$ROOT_DIR/infra/bootstrap"
OVERRIDE="$STACK/local_backend_override.tf"

if aws s3api head-bucket --bucket "$STATE_BUCKET" 2>/dev/null; then
  log "Bucket de estado já existe: aplicando bootstrap com backend S3"
  tf_init "$STACK" "bootstrap/terraform.tfstate"
  terraform -chdir="$STACK" apply -input=false -auto-approve
else
  log "Primeira execução: estado local temporário"
  printf 'terraform {\n  backend "local" {}\n}\n' > "$OVERRIDE"
  trap 'rm -f "$OVERRIDE"' EXIT
  terraform -chdir="$STACK" init -input=false -reconfigure
  terraform -chdir="$STACK" apply -input=false -auto-approve
  rm -f "$OVERRIDE"
  log "Migrando o estado do bootstrap para s3://$STATE_BUCKET"
  terraform -chdir="$STACK" init -input=false -migrate-state -force-copy \
    -backend-config="bucket=${STATE_BUCKET}" -backend-config="key=bootstrap/terraform.tfstate" \
    -backend-config="region=${AWS_REGION}" -backend-config="use_lockfile=true" -backend-config="encrypt=true"
  rm -f "$STACK/terraform.tfstate" "$STACK/terraform.tfstate.backup"
fi

log "Configurando GitHub ($GITHUB_REPO): environment demo, política de deploy e variáveis"
gh api -X PUT "repos/$GITHUB_REPO/environments/demo" --input - >/dev/null <<JSON
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
# O demo só recebe deploy da main (merge de release/hotfix).
gh api "repos/$GITHUB_REPO/environments/demo/deployment-branch-policies" -f name=main -f type=branch >/dev/null 2>&1 || true
role_arn="$(terraform -chdir="$STACK" output -json deploy_role_arns | jq -r '.demo')"
gh variable set AWS_DEPLOY_ROLE_ARN --env demo --repo "$GITHUB_REPO" --body "$role_arn"
# URL e senhas usadas pelo Newman do CD (existem depois do primeiro ./scripts/aws/infra.sh demo apply).
if url="$(aws ssm get-parameter --name "/$PROJECT/demo/api/public-base-url" --query Parameter.Value --output text 2>/dev/null)"; then
  gh variable set APP_BASE_URL --env demo --repo "$GITHUB_REPO" --body "$url"
fi
if app="$(aws secretsmanager get-secret-value --secret-id "$PROJECT-demo/app" --query SecretString --output text 2>/dev/null)"; then
  jq -r .BOOTSTRAP_ADMIN_PASSWORD <<<"$app" | gh secret set BOOTSTRAP_ADMIN_PASSWORD --env demo --repo "$GITHUB_REPO"
  jq -r .DEMO_USERS_PASSWORD <<<"$app" | gh secret set DEMO_USERS_PASSWORD --env demo --repo "$GITHUB_REPO"
fi

gh variable set AWS_REGION --repo "$GITHUB_REPO" --body "$AWS_REGION"
gh variable set ECR_REPOSITORY --repo "$GITHUB_REPO" --body "$ECR_REPOSITORY"

log "Bootstrap concluído"
terraform -chdir="$STACK" output

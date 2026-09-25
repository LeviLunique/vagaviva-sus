#!/usr/bin/env bash
# Funções comuns aos scripts de infraestrutura. Requer AWS CLI autenticado
# (ex.: aws sso login --profile vagaviva-sso) e Terraform >= 1.10.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export AWS_PROFILE="${AWS_PROFILE:-vagaviva-sso}"
export AWS_REGION="${AWS_REGION:-sa-east-1}"
export AWS_DEFAULT_REGION="$AWS_REGION"
PROJECT="vagaviva"
GITHUB_REPO="${GITHUB_REPO:-LeviLunique/vagaviva-sus}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
STATE_BUCKET="${PROJECT}-tfstate-${ACCOUNT_ID}"
ECR_REPOSITORY="${PROJECT}-api"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

tf_init() { # $1 = diretório do stack, $2 = chave do estado
  terraform -chdir="$1" init -input=false -reconfigure \
    -backend-config="bucket=${STATE_BUCKET}" \
    -backend-config="key=$2" \
    -backend-config="region=${AWS_REGION}" \
    -backend-config="use_lockfile=true" \
    -backend-config="encrypt=true"
}

log() { printf '\n\033[1;34m→ %s\033[0m\n' "$*"; }

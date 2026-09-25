#!/usr/bin/env bash
# Acompanha os logs da aplicação/banco do perfil demo (CloudWatch Logs).
# Uso: ./scripts/aws/demo-logs.sh [app|db] [--since 30m]
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
STREAM="${1:-app}"
shift || true
aws logs tail "/ec2/$PROJECT-demo" --log-stream-names "$STREAM" --follow "$@"

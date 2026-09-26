# Runbook de operação

Procedimentos para os ambientes AWS (`sa-east-1`). Pré-requisito: `aws sso login --profile vagaviva-sso` e
`export AWS_PROFILE=vagaviva-sso AWS_REGION=sa-east-1`. Todos os recursos do projeto usam o prefixo `vagaviva-`
e a tag `Project=vagaviva` — a conta é compartilhada; **nunca altere recursos sem esse prefixo**.

## 1. Deploy

| Ambiente | Como |
|---|---|
| hml | Merge em `develop` ⇒ workflow `Deploy` (quando `AWS_DEPLOY_ENABLED=true`): build ARM64 ⇒ ECR (tag = SHA) ⇒ `deploy-image.sh hml <sha>` ⇒ smoke ⇒ Newman |
| prod | Tag `vX.Y.Z` em `main` ⇒ workflow `Deploy` no environment `prod` (quando a infra de prod existir) |
| manual | `./scripts/aws/push-image.sh <sha>` e `./scripts/aws/deploy-image.sh <hml\|prod> <sha>` |
| demo | `./scripts/aws/push-image.sh <sha>`; `aws ssm put-parameter --name /vagaviva/demo/api/image-tag --value <sha> --overwrite`; na instância (SSM): `/opt/vagaviva/refresh.sh` (ou `./scripts/aws/demo-refresh.sh`) |

Conferir depois de qualquer deploy: `curl https://<cloudfront>/actuator/health` (`UP`), `flyway_schema_history` com a
migration nova e `./scripts/run-postman.sh` com `BASE_URL`/`POSTMAN_ENV` do ambiente. **Migration nova só vai para
ambiente compartilhado depois do merge** (checksum imutável).

## 2. Rollback

- **Automático:** o serviço ECS usa *deployment circuit breaker* com `rollback = true` — tasks novas que não ficam
  saudáveis são substituídas pela revisão anterior sem intervenção.
- **Manual (código):** `./scripts/aws/deploy-image.sh <env> <sha-anterior>` (as imagens são imutáveis no ECR).
- **Banco:** migrations são só para frente. Uma migration com problema é corrigida por **outra migration**; em último
  caso, restaurar o banco (seção 5) e reimplantar a imagem anterior.

## 3. Mensagens na DLQ (alarme `vagaviva-<env>-notifications-dlq`)

1. Ver o motivo: `aws logs tail /ecs/vagaviva-<env>/api --since 1h --filter-pattern '"NotificationDeliveryException"'`
   e `GET /api/v1/notifications?patientId=...` (campo `lastError`, sem dados pessoais).
2. Corrigido o motivo (provedor fora, número inválido, cota de SMS), devolver as mensagens à fila principal:
   ```bash
   aws sqs start-message-move-task --source-arn <arn-da-dlq> --destination-arn <arn-da-fila>
   aws sqs list-message-move-tasks --source-arn <arn-da-dlq>
   ```
3. O consumidor é idempotente: notificação já `SENT` é ignorada; notificação `FAILED` (5 tentativas) não é reenviada —
   se precisar reenviar, crie o marco de novo pelo fluxo de negócio.

## 4. Pausar e retomar (custo)

- `./scripts/aws/pause.sh hml` — tasks = 0 e RDS parado (a AWS religa um RDS parado após 7 dias).
- `./scripts/aws/resume.sh hml` — retoma.
- Demo: desliga sozinho após 30 min sem requisições (Lambda `vagaviva-demo-idle-shutdown`) e liga pela página de
  "iniciando" ao ser acessado; `./scripts/aws/demo-down.sh` / `demo-up.sh` para forçar.
- Destruir de vez: `./scripts/aws/infra.sh <env> destroy` (prod tem `deletion_protection` no RDS).

## 5. Restauração do banco (RPO ≤ 5 min, RTO ≤ 1 h — RNF-03)

- **PITR** (backups automáticos, 14 dias em prod):
  ```bash
  aws rds restore-db-instance-to-point-in-time --source-db-instance-identifier vagaviva-prod \
    --target-db-instance-identifier vagaviva-prod-restore --restore-time 2026-10-01T12:00:00Z \
    --db-subnet-group-name <subnet-group-do-banco> --vpc-security-group-ids <sg-do-banco> --multi-az
  ```
- **Snapshot:** `aws rds restore-db-instance-from-db-snapshot` com os mesmos parâmetros.
- Depois: apontar a aplicação para o novo endpoint (variável `DB_HOST` da task, via Terraform — `db_identifier` do
  restaurado) ou renomear as instâncias (`modify-db-instance --new-db-instance-identifier`) e reimplantar;
  validar com `flyway_schema_history` e o Newman.
- Demo (PostgreSQL no contêiner): sem PITR — `docker exec vagaviva-demo-db pg_dump` antes de mudanças arriscadas.

## 6. Rotação de segredos

| Segredo | Onde | Rotação |
|---|---|---|
| `JWT_PRIVATE_KEY` | Secrets Manager `vagaviva-<env>/app` | gerar `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \| base64 \| tr -d '\n'`, `aws secretsmanager put-secret-value`, reimplantar (`deploy-image.sh <env> <sha-atual>`). Tokens emitidos com a chave antiga deixam de valer (usuários fazem login de novo; tokens vivem 60 min) |
| `BOOTSTRAP_ADMIN_PASSWORD`, `DEMO_USERS_PASSWORD` | idem | usados só na criação inicial dos usuários; trocar a senha do usuário pela aplicação e atualizar o segredo |
| `DB_PASSWORD` | Secrets Manager `vagaviva-<env>/db` | `terraform apply -replace=random_password.db` (atualiza RDS e segredo) e reimplantar para as tasks lerem o novo valor |
| Credenciais AWS | — | não há chaves de acesso: pipeline por OIDC e operadores por IAM Identity Center (SSO) |

Todo segredo novo ou rotacionado é registrado no arquivo local de registro do operador (fora do repositório).

## 7. Incidente de segurança / LGPD

1. **Conter:** revogar acesso (desativar usuário `PATCH /users/{id}/status`, rotacionar `JWT_PRIVATE_KEY` para derrubar
   todos os tokens), bloquear IPs no WAF, pausar o ambiente se preciso.
2. **Investigar:** trilha de auditoria (`GET /api/v1/audit-events` por ator, recurso e período — inclui leituras de
   dados de pacientes e tentativas negadas), logs no CloudWatch por `traceId`, CloudTrail da conta.
3. **Comunicar:** o **ente (controlador)** comunica a ANPD e os titulares em até **3 dias úteis** quando houver risco ou
   dano relevante (Res. CD/ANPD 15/2024); o VagaViva (operador) entrega ao controlador, no mesmo prazo, a descrição dos
   dados afetados, titulares, medidas tomadas e riscos.
4. **Registrar:** documentar o incidente e as ações (retenção mínima de 5 anos, como a trilha de auditoria).

## 8. Consultas úteis (CloudWatch Logs Insights, grupo `/ecs/vagaviva-<env>/api`)

```
fields @timestamp, log.level, message, trace.id
| filter log.level = "ERROR"
| sort @timestamp desc | limit 50
```
```
fields @timestamp, message
| filter trace.id = "<traceId da resposta problem+json>"
| sort @timestamp asc
```

## 9. Alarmes e primeira ação

| Alarme | Primeira ação |
|---|---|
| `api-5xx`, `api-latency-p95` | Logs Insights (ERROR); painel `vagaviva-<env>-operacao` (CPU/memória, banco); rollback se começou após deploy |
| `api-unhealthy` | `aws ecs describe-services` (eventos); logs de inicialização (migration, segredo ausente) |
| `ecs-cpu`/`ecs-memory` | conferir autoscaling (máx. `app_max_count`); aumentar máximo ou tamanho da task |
| `rds-cpu`/`rds-storage` | consultas lentas (Performance Insights); aumentar classe/armazenamento |
| `notifications-dlq`, `notifications-failed` | seção 3 |
| `notifications-backlog` | consumidor parado? tasks saudáveis? limites do provedor de SMS? |
| `allocation-stalled` | há vagas publicadas e fila aguardando? logs do `AllocationJob`/ShedLock (`select * from shedlock`) |

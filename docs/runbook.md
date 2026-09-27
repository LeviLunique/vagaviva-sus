# Runbook de operação

O projeto tem um único ambiente, o de **demonstração** (`sa-east-1`, [ADR-0014](adr/0014-ambiente-unico-demonstracao.md)).
Pré-requisito para os comandos manuais: `aws sso login --profile vagaviva-sso` (os scripts usam esse perfil). Todos os
recursos do projeto usam o prefixo `vagaviva-` e a tag `Project=vagaviva` — a conta é compartilhada; **nunca altere
recursos sem esse prefixo**.

| Recurso | Nome |
|---|---|
| Instância (API + PostgreSQL + coletor) | EC2 `vagaviva-demo` — contêineres `vagaviva-demo-app`, `vagaviva-demo-db`, `vagaviva-demo-otel` |
| URL pública | `terraform -chdir=infra/envs/demo output api_base_url` (CloudFront) |
| Logs | CloudWatch `/ec2/vagaviva-demo` (streams `app`, `db`, `otel`); métricas EMF em `/ec2/vagaviva-demo/metrics` |
| Painel e alarmes | `vagaviva-demo-operacao`; tópico SNS `vagaviva-demo-alarms` |
| Segredos | Secrets Manager `vagaviva-demo/app` e `vagaviva-demo/db` |
| Parâmetros | `/vagaviva/demo/api/image-tag`, `/vagaviva/demo/api/public-base-url`, `/vagaviva/demo/runtime/docker-compose`, `/vagaviva/demo/runtime/otel-config` |

## 1. Deploy

- **Automático (CD):** merge em `main` ⇒ workflow `Deploy` (quando `AWS_DEPLOY_ENABLED=true`): build ARM64 ⇒ ECR (tag =
  SHA) ⇒ `scripts/aws/demo-deploy.sh` (grava a tag, liga a instância se estiver parada, roda o `refresh.sh` via SSM e
  espera `UP` pela CloudFront) ⇒ Newman no ambiente. Também pode ser disparado manualmente (*Run workflow*).
- **Manual:** `./scripts/aws/demo-refresh.sh [sha]` — publica a imagem do commit e segue o mesmo caminho.
- **Infraestrutura:** `./scripts/aws/infra.sh demo plan|apply`. Mudanças no compose ou no coletor vão para os parâmetros
  do SSM e entram no próximo `refresh.sh` (deploy ou boot) — a instância não é recriada (o `user_data` é ignorado
  depois do primeiro boot, para não apagar o banco).

Conferir depois de qualquer deploy: `curl <url>/actuator/health` (`UP`, não a página de "iniciando"),
`flyway_schema_history` com a migration nova (`./scripts/aws/demo-logs.sh app` ou SSM) e o Newman
(`BASE_URL=<url> POSTMAN_ENV=demo ./scripts/run-postman.sh` com as senhas do segredo `vagaviva-demo/app`).
**Migration nova só vai para o demo depois do merge** — o checksum de uma migration aplicada é imutável.

## 2. Rollback

- **Código:** `./scripts/aws/demo-deploy.sh <sha-anterior>` (as imagens são imutáveis no ECR; a tag anterior está em
  `git log` da `main` ou no histórico do workflow `Deploy`).
- **Banco:** migrations são só para frente. Uma migration com problema é corrigida por **outra migration**; em último
  caso, restaurar o backup (seção 5) e aplicar a imagem anterior.

## 3. Mensagens na DLQ (alarme `vagaviva-demo-notifications-dlq`)

1. Ver o motivo: `./scripts/aws/demo-logs.sh app --since 1h | grep NotificationDeliveryException` e
   `GET /api/v1/notifications?patientId=...` (campo `lastError`, sem dados pessoais).
2. Corrigido o motivo (provedor fora, número inválido, cota de SMS), devolver as mensagens à fila principal:
   ```bash
   aws sqs start-message-move-task --source-arn <arn-da-dlq> --destination-arn <arn-da-fila>
   aws sqs list-message-move-tasks --source-arn <arn-da-dlq>
   ```
3. O consumidor é idempotente: notificação já `SENT` é ignorada; notificação `FAILED` (5 tentativas) não é reenviada —
   se precisar reenviar, crie o marco de novo pelo fluxo de negócio.

## 4. Ligar, desligar e custo

- Liga sozinha no primeiro acesso (página de "iniciando", ~60–90 s) e desliga após 30 min sem requisições na CloudFront
  (Lambda `vagaviva-demo-idle-shutdown`, com carência de 30 min depois de cada boot).
- Antes de uma gravação ou apresentação: `./scripts/aws/demo-up.sh` (espera o `UP`). Para desligar já: `demo-down.sh`.
- A métrica da CloudFront chega com alguns minutos de atraso: depois de um período ocioso longo, a instância pode ser
  desligada poucos minutos depois de voltar a ser usada; o próximo acesso a religa.
- Destruir tudo: `./scripts/aws/infra.sh demo destroy` (apaga também o banco de demonstração).

## 5. Backup e restauração do banco

O PostgreSQL roda num contêiner na própria instância (volume `pgdata` no disco EBS criptografado) — sem PITR.
```bash
# backup (via SSM Session Manager na instância)
docker exec vagaviva-demo-db pg_dump -U vagaviva -Fc vagaviva > /tmp/vagaviva-$(date +%F).dump
# restauração
docker exec -i vagaviva-demo-db pg_restore -U vagaviva -d vagaviva --clean --if-exists < /tmp/vagaviva-AAAA-MM-DD.dump
```
Sem backup, o ambiente se recompõe sozinho a partir de um banco vazio: o Flyway recria o esquema, o seed de demonstração
recria unidades, especialidades e pacientes fictícios, e os usuários de demonstração são recriados no boot.

## 6. Rotação de segredos

| Segredo | Onde | Rotação |
|---|---|---|
| `JWT_PRIVATE_KEY` | `vagaviva-demo/app` | gerar `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \| base64 \| tr -d '\n'`, `aws secretsmanager put-secret-value`, reaplicar (`demo-deploy.sh <sha-atual>`). Tokens emitidos com a chave antiga deixam de valer (tokens vivem 60 min) |
| `BOOTSTRAP_ADMIN_PASSWORD`, `DEMO_USERS_PASSWORD` | `vagaviva-demo/app` | usados na criação dos usuários; trocar também os segredos do Environment `demo` no GitHub (Newman do CD) |
| `DB_PASSWORD` | `vagaviva-demo/db` | o banco é criado com a senha do segredo no primeiro boot; para trocar, `ALTER USER` no contêiner e atualizar o segredo, depois reaplicar |
| Credenciais AWS | — | não há chaves de acesso: CD por OIDC (role `vagaviva-gha-deploy-demo`) e operadores por IAM Identity Center (SSO) |

Todo segredo novo ou rotacionado é registrado no arquivo local de registro do operador (fora do repositório).

## 7. Incidente de segurança / LGPD

1. **Conter:** revogar acesso (desativar usuário `PATCH /users/{id}/status`, rotacionar `JWT_PRIVATE_KEY` para derrubar
   todos os tokens), desligar a instância se preciso (`demo-down.sh`).
2. **Investigar:** trilha de auditoria (`GET /api/v1/audit-events` por ator, recurso e período — inclui leituras de
   dados de pacientes e tentativas negadas), logs por `traceId` no CloudWatch, traces no X-Ray, CloudTrail da conta.
3. **Comunicar:** numa implantação real, o **ente (controlador)** comunica a ANPD e os titulares em até **3 dias úteis**
   quando houver risco ou dano relevante (Res. CD/ANPD 15/2024), com o apoio do operador (descrição dos dados afetados,
   titulares, medidas tomadas e riscos). O demo só tem dados fictícios.
4. **Registrar:** documentar o incidente e as ações.

## 8. Consultas úteis (CloudWatch Logs Insights, grupo `/ec2/vagaviva-demo`)

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
| `vagaviva-demo-notifications-dlq` | seção 3 |
| `vagaviva-demo-notifications-failed` | provedor de SMS/WhatsApp fora ou canal mal configurado — logs do `NotificationDispatchService`; no modo `SANDBOX` não deve disparar |
| API sem resposta | `demo-up.sh`; logs (`demo-logs.sh app`) — migration, segredo ausente, memória; `docker ps` via SSM |

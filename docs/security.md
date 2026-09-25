# Segurança e LGPD

## 1. Base legal e papéis
- Dados de saúde são **dados pessoais sensíveis** (LGPD art. 5º, II).
- Base legal: execução de políticas públicas pela administração (art. 7º, III e art. 11, II, "b") e tutela da saúde (art. 11, II, "f"). O ente público (secretaria de saúde) é o **controlador**; o VagaViva atua como **operador**.
- Princípio da necessidade (art. 6º, III): cada mensagem e cada resposta contém o mínimo necessário.
- Incidentes: comunicação à ANPD e aos titulares em até 3 dias úteis (Resolução CD/ANPD 15/2024).

## 2. Controles por ameaça (STRIDE)

| Ameaça | Controles |
|---|---|
| Falsificação de identidade | JWT RS256 de curta duração; senhas com BCrypt; bloqueio após tentativas inválidas; links do paciente com token aleatório de 128 bits guardado apenas como hash; respostas genéricas na consulta pública |
| Adulteração | TLS na borda; validação de entrada; controle de concorrência otimista; WAF com regras gerenciadas (SQLi, XSS, entradas maliciosas) |
| Repúdio | Trilha de auditoria de leituras de dados pessoais e de ações de regulação/agenda; logs com identificador de trace; CloudTrail |
| Vazamento de informação | Mascaramento de CNS/CPF; dados pessoais nunca em URL; mensagens sem diagnóstico ou especialidade sensível; logs sem dados pessoais; criptografia em repouso (RDS, SQS, Secrets Manager, S3); dados na região `sa-east-1`; ALB interno e banco sem rota para a internet |
| Negação de serviço | Rate limit no WAF (rotas públicas e global); autoscaling; filas com DLQ; limites de tamanho de lote |
| Elevação de privilégio | Autorização por papel e por unidade de saúde; testes de acesso negado (403) por endpoint; IAM de menor privilégio; deploy via OIDC restrito ao environment do GitHub; contêiner sem root |

## 3. Cadeia de entrega
- Varredura de dependências e segredos (Trivy) e análise estática (CodeQL) em todo PR.
- Imagens com tag imutável e varredura no push (ECR).
- Nenhuma credencial em repositório: segredos no Secrets Manager e no GitHub (environments).

## 4. Checklist OWASP Top 10 (2021)

| Item | Situação |
|---|---|
| A01 Broken Access Control | RBAC + escopo por unidade; negar por padrão (implementado); testes 403 por endpoint |
| A02 Cryptographic Failures | TLS, criptografia em repouso, BCrypt, RS256 |
| A03 Injection | JPA com parâmetros, Bean Validation, WAF |
| A04 Insecure Design | máquinas de estado explícitas, idempotência, limites de negócio (Event Storming/SPEC) |
| A05 Security Misconfiguration | Actuator restrito a health/info, erros sem stack trace, IaC revisado |
| A06 Vulnerable Components | Trivy + CodeQL no CI, versões atualizadas |
| A07 Identification and Authentication Failures | bloqueio por tentativas, política de senha, expiração de token |
| A08 Software and Data Integrity Failures | actions de terceiros fixadas por SHA, OIDC, tags imutáveis |
| A09 Security Logging and Monitoring Failures | auditoria, logs estruturados, alarmes |
| A10 SSRF | a aplicação não faz requisições para URLs fornecidas pelo usuário |

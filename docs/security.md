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

## 3. Decisões registradas
- **CSRF desabilitado (`SecurityConfig`)**: a API é *stateless* — autenticação exclusivamente pelo cabeçalho `Authorization: Bearer`, sem cookies nem sessão no servidor. Como o navegador não anexa credenciais automaticamente, não há vetor de CSRF; manter o filtro exigiria tokens CSRF sem benefício. O alerta correspondente da análise estática foi avaliado e dispensado com esta justificativa. Se algum dia houver autenticação por cookie (ex.: front-end com sessão), o CSRF deve ser reativado para essas rotas.
- **Swagger/OpenAPI desligados em produção**: a documentação interativa só existe em local, homologação e demo (perfil `demo`). Em produção, a raiz da API devolve apenas um índice JSON com o link do health check — nenhum mapa da API exposto na internet.
- **Login sem oráculo de contas (F1)**: e-mail inexistente, senha errada, usuário inativo ou bloqueado recebem o mesmo `401 INVALID_CREDENTIALS`, e a senha é verificada com BCrypt em todos os caminhos (contra um hash fictício quando o e-mail não existe) — nem o conteúdo nem o tempo de resposta revelam se a conta existe. Cada tentativa é auditada (`LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGIN_LOCKED`) com o IP de origem, sem registrar o e-mail digitado. A contagem de falhas e a auditoria sobrevivem à resposta de erro (a transação não é desfeita pela exceção de credencial).
- **Token com dados mínimos**: o JWT leva `sub` (id), papel, unidade e nome — sem e-mail nem outros dados pessoais. A chave RS256 vem do Secrets Manager e é obrigatória no perfil `aws` (a aplicação não sobe sem ela); localmente, sem chave, gera-se um par efêmero. O token vale 60 minutos: desativar um usuário bloqueia novos logins imediatamente, e tokens já emitidos expiram nesse prazo.
- **Erros 401/403 no mesmo formato**: os filtros de segurança delegam ao `GlobalExceptionHandler`, que responde `application/problem+json` (RFC 9457) com `code` estável e sem detalhes internos.
- **Dados de pacientes (F2)**: CNS e CPF são validados pelos algoritmos oficiais e **sempre mascarados** nas respostas (`***********1234`, `***.***.***-34`); a busca por documento usa `POST` com o documento no corpo, nunca na URL (que vai para logs de proxy e CDN). Cadastro, leituras, buscas e alteração de contato são auditados; **buscas sem resultado também** (`PATIENT_SEARCHED`/`FAILURE`), o que permite detectar tentativas de enumeração de documentos — e a trilha nunca guarda o documento pesquisado. O resumo entregue a outros módulos (`PatientApi`) não contém CNS, CPF nem nome completo.
- **Vínculo profissional–unidade (RN-03)**: REQUESTER só pode ser vinculado a UBS e SCHEDULER só a unidade executante; a unidade precisa existir e estar ativa.
- **Links do paciente sem login**: o token (128 bits, aleatório, armazenado só como hash, com validade) funciona como credencial de uso restrito a um agendamento/oferta; as ações são idempotentes e auditadas.

## 4. Cadeia de entrega
- Varredura de dependências e segredos (Trivy) e análise estática (CodeQL) em todo PR.
- Imagens com tag imutável e varredura no push (ECR).
- Nenhuma credencial em repositório: segredos no Secrets Manager e no GitHub (environments).

## 5. Checklist OWASP Top 10 (2021)

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

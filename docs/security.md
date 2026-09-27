# Segurança e LGPD

## 1. Base legal e papéis
- Dados de saúde são **dados pessoais sensíveis** (LGPD art. 5º, II).
- Base legal: execução de políticas públicas pela administração (art. 7º, III e art. 11, II, "b") e tutela da saúde (art. 11, II, "f"). O ente público (secretaria de saúde) é o **controlador**; o VagaViva atua como **operador**.
- Princípio da necessidade (art. 6º, III): cada mensagem e cada resposta contém o mínimo necessário.
- Incidentes: comunicação à ANPD e aos titulares em até 3 dias úteis (Resolução CD/ANPD 15/2024).

## 2. Controles por ameaça (STRIDE)

> **Ambientes (ADR-0014):** o único ambiente provisionado é o de demonstração, com WAF desligado (custo fixo) e dados só fictícios. Os controles de borda citados abaixo (WAF, ALB interno, RDS) são do desenho para produção real; os controles de aplicação (autenticação, escopo por unidade, limites de requisição, cabeçalhos, auditoria, retenção, logs sem PII) valem igualmente no demo.

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
- **Swagger/OpenAPI desligados em produção**: a documentação interativa só existe em local e no demo (perfil `demo`). Em produção, a raiz da API devolve apenas um índice JSON com o link do health check — nenhum mapa da API exposto na internet.
- **Login sem oráculo de contas (F1)**: e-mail inexistente, senha errada, usuário inativo ou bloqueado recebem o mesmo `401 INVALID_CREDENTIALS`, e a senha é verificada com BCrypt em todos os caminhos (contra um hash fictício quando o e-mail não existe) — nem o conteúdo nem o tempo de resposta revelam se a conta existe. Cada tentativa é auditada (`LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGIN_LOCKED`) com o IP de origem, sem registrar o e-mail digitado. A contagem de falhas e a auditoria sobrevivem à resposta de erro (a transação não é desfeita pela exceção de credencial).
- **Token com dados mínimos**: o JWT leva `sub` (id), papel, unidade e nome — sem e-mail nem outros dados pessoais. A chave RS256 vem do Secrets Manager e é obrigatória no perfil `aws` (a aplicação não sobe sem ela); localmente, sem chave, gera-se um par efêmero. O token vale 60 minutos: desativar um usuário bloqueia novos logins imediatamente, e tokens já emitidos expiram nesse prazo.
- **Erros 401/403 no mesmo formato**: os filtros de segurança delegam ao `GlobalExceptionHandler`, que responde `application/problem+json` (RFC 9457) com `code` estável e sem detalhes internos.
- **Dados de pacientes (F2)**: CNS e CPF são validados pelos algoritmos oficiais e **sempre mascarados** nas respostas (`***********1234`, `***.***.***-34`); a busca por documento usa `POST` com o documento no corpo, nunca na URL (que vai para logs de proxy e CDN). Cadastro, leituras, buscas e alteração de contato são auditados; **buscas sem resultado também** (`PATIENT_SEARCHED`/`FAILURE`), o que permite detectar tentativas de enumeração de documentos — e a trilha nunca guarda o documento pesquisado. O resumo entregue a outros módulos (`PatientApi`) não contém CNS, CPF nem nome completo.
- **Transparência pública (F3)**: a consulta do cidadão exige protocolo **e** data de nascimento, ambos no corpo do `POST`; protocolo inexistente e data divergente recebem exatamente o mesmo `404`, e cada tentativa é auditada (as falhas sem identificar o encaminhamento). A resposta não traz nome, CNS nem dados clínicos, e especialidade marcada como sensível aparece só como "Consulta/Exame especializado". As estatísticas públicas são agregadas por especialidade. Em produção, o WAF limita a taxa das rotas `/api/v1/public/**`.
- **Dados clínicos do encaminhamento**: justificativa e CID aparecem apenas no detalhe (leitura auditada) para o regulador, o ADMIN e a UBS de origem; listagens e a fila não os exibem. REQUESTER só vê encaminhamentos da própria unidade (RN-03).
- **Agenda (F4)**: SCHEDULER publica, cancela, consulta e registra comparecimento apenas na própria unidade executante; agendamentos identificam o paciente só por primeiro nome e CNS mascarado; leitura de agendamento, alocação, cancelamento e comparecimento são auditados.
- **Vínculo profissional–unidade (RN-03)**: REQUESTER só pode ser vinculado a UBS e SCHEDULER só a unidade executante; a unidade precisa existir e estar ativa.
- **Links do paciente sem login (F5)**: o token (16 bytes de `SecureRandom`, Base64URL de 22 caracteres) funciona como credencial de uso restrito a um agendamento e vale até o início do atendimento. Só o SHA-256 fica no banco — um vazamento da base não permite agir pelo paciente —, cada mensagem tem o seu token e o valor em claro existe apenas no texto enviado. Formato inválido é recusado antes de consultar o banco; inexistente responde `404` e expirado `410`, ambos auditados (`PATIENT_LINK_REJECTED`, com IP e sem o token). A página mostra só o primeiro nome, "consulta"/"exame" (nunca a especialidade), data e unidade; as ações são idempotentes e auditadas com o IP de origem. Em produção, o WAF limita a taxa de `/api/v1/patient-actions/**` e `/p/**` (F8).
- **Ofertas de encaixe (F6)**: o link da oferta usa o mesmo token de 128 bits e vence junto com a oferta (410 depois do prazo); link de agendamento não aceita oferta nem vice-versa (404). A página mostra o mesmo mínimo do agendamento; aceite e recusa são auditados com o IP. Aceites simultâneos são resolvidos no banco (ADR-0013), sem janela para dois pacientes na mesma vaga.
- **Logs sem dados pessoais (F7)**: `LogPrivacyIT` percorre cadastro e busca de paciente, encaminhamento, regulação, alocação, envio da mensagem e uso do link, capturando todos os logs (mensagem, MDC e exceções), e falha se aparecer CNS, CPF, telefone, nome completo ou token. Os logs levam o `traceId` para correlação, nunca o dado do paciente.
- **Mensagens ao paciente (F5)**: texto mínimo (primeiro nome, rótulo genérico, data, unidade, link), sem CNS, CPF ou especialidade; o log de notificações expõe apenas status, canal e tentativas — nunca o texto nem o telefone (guardado só como hash do destino). A caixa sandbox com o texto existe apenas nos perfis de demonstração (ADMIN).

## 4. Cadeia de entrega
- Varredura de dependências e segredos (Trivy) e análise estática (CodeQL) em todo PR.
- Imagens com tag imutável e varredura no push (ECR).
- Nenhuma credencial em repositório: segredos no Secrets Manager e no GitHub (environments).

## 5. Checklist OWASP Top 10 (2021) — revisão da F8

Cada item com o controle e onde ele é verificado. Revisão feita na F8 (2026-09-26); correções aplicadas estão marcadas com **F8**.

| Item | Controle | Evidência |
|---|---|---|
| A01 Broken Access Control | Negar por padrão; RBAC por papel e **escopo por unidade** (REQUESTER/SCHEDULER só na própria unidade; log de notificações e ofertas só com recurso da própria unidade); links do paciente restritos ao agendamento/oferta do token | `SecurityConfig`, testes 403 em todos os controllers (`*ControllerTest`), `RegulationFlowIT` (REFERRAL_OUT_OF_UNIT), Postman (403 por pasta) |
| A02 Cryptographic Failures | HTTPS obrigatório na borda (CloudFront redireciona HTTP); da CloudFront à origem (EC2 no demo; ALB interno no desenho de produção) o tráfego segue pela rede privada da AWS (VPC origin, sem exposição à internet) — **pendência:** com o certificado padrão da CloudFront não é possível fixar o protocolo mínimo; em produção, domínio próprio com certificado ACM e política `TLSv1.2_2021`; RDS/SQS/Secrets/S3 criptografados; BCrypt 12; JWT RS256 com chave do Secrets Manager; token do paciente guardado só como SHA-256 | `infra/stack`, `PasswordPolicy`, `JwtKeyConfig`, `PatientActionToken` |
| A03 Injection | JPA/JDBC sempre com parâmetros (nenhuma SQL concatenada com entrada); Bean Validation; WAF no desenho de produção (SQLi, KnownBadInputs, CommonRuleSet; opcional no demo, `enable_waf`) | adaptadores `*PersistenceAdapter` |
| A04 Insecure Design | Máquinas de estado explícitas, idempotência (ações do paciente, projeções, consumidor SQS), atualização condicional no aceite de encaixe (ADR-0013), limites de negócio (lote de 200 vagas, página ≤ 100) | `ReferralStatus`/`SlotStatus`, `OfferRaceIT`, `AllocationConcurrencyIT` |
| A05 Security Misconfiguration | Actuator só `health`/`info`; erros sem stack trace (`problem+json`); Swagger desligado em produção; **F8:** cabeçalhos `Referrer-Policy: no-referrer` (o token do paciente está na URL), `Content-Security-Policy: default-src 'none'`, `Permissions-Policy`, `X-Frame-Options`, `nosniff`, `Cache-Control: no-store`; **CORS fechado explícito** | `SecurityHeadersTest`, `GlobalExceptionHandlerTest` |
| A06 Vulnerable Components | Trivy em todo PR (falha em CRITICAL; relatório de HIGH) e CodeQL; versões atuais (Java 25, Boot 4.1.1, Modulith 2.1.1) | workflows `ci.yml`/`codeql.yml`, checks obrigatórios do ruleset |
| A07 Identification and Authentication Failures | Bloqueio após 5 falhas por 15 min; resposta sem oráculo de contas; política de senha; token de 60 min; links do paciente com 128 bits de entropia e validade | `StaffUserTest`, `AuthFlowIT`, `PatientActionTokenTest` |
| A08 Software and Data Integrity Failures | Actions de terceiros fixadas por SHA; OIDC com subject imutável; imagens no ECR com tag imutável (SHA do commit); outbox transacional (evento não se perde nem é publicado sem commit) | `.github/workflows`, `bootstrap.sh`, ADR-0004 |
| A09 Security Logging and Monitoring Failures | Trilha de auditoria de leituras de dados pessoais e de ações; tentativas negadas auditadas (login, links, busca sem resultado); logs JSON com `traceId` e **sem PII**; alarmes técnicos e de negócio | `LogPrivacyIT`, `RetentionIT`, `observability.tf` |
| A10 SSRF | A aplicação não faz requisições para URLs fornecidas pelo usuário (só SQS/SNS/WhatsApp da AWS, com endereços fixos) | revisão de código |

**OWASP API Security Top 10 (2023) — itens específicos de API:**
- **API1 BOLA:** todo recurso por id verifica o escopo do usuário (unidade) ou o token do paciente; ids são UUIDv7 (não sequenciais).
- **API4 Consumo irrestrito de recursos (F8):** corpo limitado a 256 KB (`RequestBodyLimitFilter`, 413 — inclusive corpo *chunked*), cabeçalhos até 16 KB, páginas ≤ 100, lote ≤ 200 vagas, e, no desenho de produção, WAF com limite de taxa (100/5 min por IP nas rotas públicas, incluindo os links do paciente e o link curto `/p/`, e 2.000/5 min global; no demo o WAF é opcional e só tem o limite global).
- **API5 Autorização por função:** `@PreAuthorize` em cada endpoint administrativo; recursos de demonstração só existem com `vagaviva.demo.enabled`.
- **API8 Configuração:** ver A05.

**Retenção (F8, SPEC §10):** texto das mensagens apagado após 90 dias (o registro de envio permanece) e trilha de auditoria apagada após 5 anos — jobs diários com ShedLock (`NotificationRetentionJob`, `AuditRetentionJob`), verificados por `RetentionIT`.

**Teste de carga (F8):** executado no desenho de produção recriado temporariamente, com o IP do gerador liberado por uma regra de *allowlist* no WAF só durante o teste ([capacity-planning.md §6](capacity-planning.md)).

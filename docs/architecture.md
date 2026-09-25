# Arquitetura do VagaViva

## 1. Contexto (C4 — nível 1)

```mermaid
flowchart LR
  pac([Paciente]) -- WhatsApp/SMS + link --> vv[VagaViva API]
  ubs([Operador da UBS]) --> vv
  reg([Médico regulador]) --> vv
  exe([Agendador da unidade executante]) --> vv
  ges([Gestor da secretaria]) --> vv
  cid([Cidadão]) -- posição e estatísticas públicas --> vv
  vv -- SMS --> sns[Amazon SNS]
  vv -. evolução .-> wa[WhatsApp Business]
  vv -. evolução .-> rnds[RNDS / MIRA - FHIR R4]
```

O VagaViva é *API-first*: complementa os sistemas de regulação já usados por estados e municípios, sem exigir troca de sistema.

## 2. Implantação na AWS (C4 — nível 2)

```mermaid
flowchart TB
  u([Usuários e pacientes]) --> cf[CloudFront HTTPS + AWS WAF]
  cf -- VPC origin --> alb[ALB interno]
  subgraph vpc[VPC sa-east-1 - 2 AZs]
    subgraph priv[Subnets privadas]
      alb --> ecs[ECS Fargate ARM64<br/>API Spring Boot + sidecar OpenTelemetry]
    end
    subgraph dbnet[Subnets de banco - sem internet]
      rds[(RDS PostgreSQL 17<br/>Multi-AZ em produção)]
    end
    ecs --> rds
  end
  ecs <--> sqs[[SQS notificações + DLQ]]
  ecs --> sns[SNS SMS]
  ecs --> sm[Secrets Manager]
  ecs --> obs[CloudWatch Logs/Métricas + X-Ray]
  gh[GitHub Actions - OIDC] --> ecr[ECR] --> ecs
```

| Camada | Serviço | Por quê |
|---|---|---|
| Borda | CloudFront + WAF | HTTPS sem domínio próprio, HTTP/3, cabeçalhos de segurança, regras gerenciadas (SQLi, XSS, IPs maliciosos) e *rate limit* nas rotas públicas |
| Entrada | ALB **interno** via VPC origin | nenhum endpoint da aplicação exposto diretamente à internet |
| Computação | ECS Fargate ARM64 (Graviton) | contêiner gerenciado, sem servidores para manter, autoscaling por CPU e por requisições, rollback automático de deploy |
| Dados | RDS PostgreSQL 17 | transações ACID para alocação de vagas, consultas de fila e indicadores, backup/PITR, Multi-AZ |
| Mensageria | SQS + DLQ | absorve picos de envio, isola o provedor de SMS/WhatsApp, retentativas e fila de falhas |
| Segredos | Secrets Manager | chave JWT, credenciais do banco e senhas iniciais fora do código |
| Observabilidade | CloudWatch + X-Ray (via ADOT) | logs estruturados, métricas técnicas e de negócio, traces, alarmes e painel |

Ambientes: **hml** (custo mínimo: Fargate Spot, RDS single-AZ, tasks sem regra de entrada além do ALB) e **prod** (Multi-AZ, NAT por AZ, tasks e banco isolados em subnets privadas).

## 3. Módulos (bounded contexts)

| Módulo | Responsabilidade |
|---|---|
| `shared` | Kernel compartilhado: segurança, erros (RFC 9457), ids, relógio, OpenAPI |
| `audit` | Trilha de auditoria de acesso a dados pessoais (LGPD) |
| `identity` | Profissionais, papéis e autenticação (JWT RS256) |
| `catalog` | Unidades de saúde (CNES), área de atendimento e especialidades |
| `patient` | Pacientes (CNS/CPF), contato, preferências e grupos prioritários |
| `regulation` | Encaminhamentos, regulação por risco, fila priorizada e transparência pública |
| `scheduling` | Vagas, motor de alocação, agendamentos, prazos de confirmação, comparecimento e política de liberação |
| `reallocation` | Ofertas de encaixe em cascata para vagas liberadas de última hora |
| `engagement` | Notificações, canais (SMS/WhatsApp/sandbox), links e ações do paciente |
| `insights` | Projeções e indicadores do gestor (absenteísmo, reaproveitamento, espera) |

Cada módulo segue a arquitetura hexagonal:

```
<modulo>/
├── <Modulo>Api.java        ← fronteira pública do módulo
├── events/                 ← eventos de domínio publicados
├── domain/                 ← agregados, value objects, políticas (Java puro)
├── application/            ← casos de uso (port/in), portas de saída (port/out), serviços
└── adapter/                ← in: web, jobs, listeners · out: persistência, integrações
```

As regras são verificadas por testes: `ModularityTest` (ciclos e acesso a internals entre módulos) e `HexagonalArchitectureTest` (dependências entre camadas).

## 4. Fluxos principais

### 4.1 Alocação e confirmação ativa

```mermaid
sequenceDiagram
  participant EXE as Unidade executante
  participant API as VagaViva
  participant DB as PostgreSQL
  participant Q as SQS
  participant P as Paciente
  EXE->>API: POST /slots (agenda)
  API->>DB: vagas AVAILABLE
  API->>DB: próximo da fila (risco, grupo prioritário, espera)<br/>FOR UPDATE SKIP LOCKED
  API->>DB: agendamento PENDING_CONFIRMATION + evento (outbox)
  API->>Q: NotificationDispatchRequested
  Q->>API: consumidor de notificações
  API->>P: SMS/WhatsApp com link
  P->>API: POST /patient-actions/{token}/confirm
  API->>DB: CONFIRMED
```

### 4.2 Vaga liberada → encaixe em cascata

```mermaid
sequenceDiagram
  participant P1 as Paciente A
  participant API as VagaViva
  participant P2 as Pacientes B, C, D
  P1->>API: cancelar ("não posso ir")
  API->>API: Paciente A volta à fila na mesma posição
  API->>API: política de liberação<br/>≥ 5 dias: realoca pela fila · 2h–5 dias: encaixe · < 2h: perdida
  API->>P2: oferta de encaixe (lote de 3, prazo de resposta)
  P2->>API: aceitar
  API->>API: atualização atômica da vaga (primeiro vence)
  API-->>P2: demais recebem "vaga já preenchida"
```

## 5. Estratégia de dados e consistência
- Cada módulo é dono das suas tabelas; relações entre módulos por identificador, validadas pela API do módulo.
- Eventos de domínio são gravados na mesma transação da mudança (registro de publicação do Spring Modulith — *outbox*) e reentregues em caso de falha.
- Consistência forte no núcleo (alocação, aceite de oferta) com bloqueio pessimista (`SKIP LOCKED`) e atualização condicional; consistência eventual para notificações e indicadores.
- Leituras de alto volume (posição pública na fila, indicadores) usam *read models* recalculados/projetados (CQRS leve).

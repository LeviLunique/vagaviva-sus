# Dimensionamento (capacity planning)

## 1. Demanda de referência

| Premissa | Valor | Fonte |
|---|---|---|
| Usuários exclusivos do SUS | 160,3 milhões | IBGE (2025) e ANS (2026) |
| Consultas médicas especializadas realizadas | 128,4 milhões/ano | DATASUS/SIA (2025) |
| Exames diagnósticos não laboratoriais | ~198 milhões/ano | DATASUS/SIA (2025) |
| Pessoas na fila do Sisreg | 5,7 milhões (sem SP, RJ e BH) | Ministério da Saúde via LAI (jan/2025) |
| Absenteísmo em atenção especializada | 25% a 43% | Saúde em Debate (2019), Prefeitura de SP (2025), SES-RJ (2026) |

Agendamentos regulados (consultas + exames) estimados em três cenários (0,20 / 0,46 / 0,77 por usuário do SUS por ano):

| Cenário | Estado com 10 mi de usuários SUS | Brasil |
|---|---|---|
| Conservador | ~170 mil/mês | ~2,7 mi/mês |
| **Moderado** | **~386 mil/mês (~18 mil por dia útil)** | ~6,2 mi/mês |
| Agressivo | ~642 mil/mês | ~10,3 mi/mês |

**Ponto de projeto:** 1 milhão de agendamentos/dia e 4 milhões de mensagens/dia por implantação — o cenário nacional agressivo com folga de 2 a 3 vezes para picos.

## 2. Carga derivada

| Grandeza | Por agendamento (ciclo de vida) | No ponto de projeto (janela útil de 12 h, pico 3×) |
|---|---|---|
| Escritas HTTP | ~5 (encaminhar, regular, alocar, confirmar/cancelar, comparecimento) | média ~116/s · **pico ~350/s** |
| Leituras HTTP | ~20 (listas, consultas, link do paciente, posição na fila) | média ~463/s · **pico ~1.400/s** |
| Mensagens | ~4 (entrada na fila, agendamento, lembrete, véspera) | ~93/s, suavizadas pela fila SQS |
| Armazenamento | ~6 KB | estado moderado ~28 GB/ano · nacional moderado ~450 GB/ano |

## 3. Capacidade da solução (desenho para produção real)

O projeto provisiona apenas o ambiente de demonstração (ADR-0014). O desenho abaixo é o de uma implantação real num ente; foi implementado em Terraform e medido na F8 (seção 6) antes de sair do repositório.

| Componente | Configuração | Capacidade / justificativa |
|---|---|---|
| API (ECS Fargate) | tasks de 1 vCPU / 2 GB, virtual threads, autoscaling por CPU (60%) e por requisições por task | **medido na F8: 300 req/s em uma task com p95 de 29 ms no ALB e 40% de CPU** (seção 6) ⇒ pico nacional (~1.750 req/s) ≈ 4 tasks a 60% de CPU; máximo de 10 com folga |
| Banco (RDS PostgreSQL) | produção inicial `db.t4g.medium` Multi-AZ; cenário nacional `db.m7g.large/xlarge` + réplica de leitura | escritas curtas e indexadas (≈ 1,5 mil linhas/s no pico nacional); índices parciais para a fila e a alocação |
| Fila (SQS) | padrão + DLQ, consumo concorrente limitado | vazão praticamente ilimitada; controla o ritmo de envio conforme os limites do provedor |
| Borda (CloudFront + WAF) | rate limit por IP nas rotas públicas | protege a consulta pública contra enumeração e picos anômalos |

**Decisão que elimina o maior pico:** a alocação é *push* — o sistema distribui as vagas à fila priorizada. Não há a corrida de "abertura de agenda" em que milhares de pacientes disputam as mesmas vagas ao mesmo tempo.

**Piloto estadual** (~20–30 mil agendamentos por dia útil ⇒ menos de 20 req/s de pico): 2 tasks e `db.t4g.medium` atendem com folga.

## 4. Custos estimados de operação (desenho para produção real)

| Item | Homologação | Produção (piloto estadual) |
|---|---|---|
| Computação (Fargate) | ~US$ 10 (Spot, 1 task) | ~US$ 70 (2 tasks) |
| Banco (RDS) | ~US$ 20 (micro, single-AZ) | ~US$ 130 (medium Multi-AZ) |
| Rede (ALB, NAT, CloudFront) | ~US$ 25 | ~US$ 120 |
| WAF, segredos, logs e métricas | ~US$ 20 | ~US$ 40 |
| **Total de infraestrutura** | **~US$ 75/mês** | **~US$ 360/mês** |
| Mensageria (80% WhatsApp / 20% SMS) | — | ~R$ 0,29 por agendamento (≈ R$ 112 mil/mês no cenário moderado estadual) |

Custo de mensageria por atendimento recuperado: **~R$ 3,90** (redução de 7,5 p.p. no absenteísmo), frente a R$ 10,77–R$ 28,26 de valor de tabela SUS de cada consulta/exame desperdiçado — o custo real para o gestor é bem maior que a tabela.

## 5. Ambiente de demonstração (o único ambiente do projeto)

Não atende a demanda real do SUS — é para demonstrar o MVP completo com custo mínimo. Ver ADR-0012 e ADR-0014.

| Item | Configuração | Custo |
|---|---|---|
| Computação | 1 EC2 `t4g.medium` (app + PostgreSQL no mesmo host), desliga sozinha após 30 min sem acesso e religa sozinha no próximo acesso | ~US$ 0,0536/h só enquanto ligada — para ~10h de uso concentrado (ensaios + gravação): **~US$ 0,55** |
| Disco (EBS gp3, 20 GB) | cobrado o tempo todo, mesmo desligada | ~US$ 1,60/mês |
| Rede (CloudFront + 2 Lambdas pequenas + EventBridge Scheduler) | sem ALB, sem NAT, sem IP elástico | ~US$ 1–2/mês (majoritariamente dentro do nível gratuito) |
| Fila (SQS), Secrets Manager, logs | volume mínimo | ~US$ 1/mês |
| Observabilidade (métricas customizadas `VagaViva`, X-Ray, painel) | métricas cobradas por hora de publicação — só com a instância ligada | centavos a poucos dólares no mês |
| **Total** | | **~US$ 5–10 no mês da apresentação**, contra ~US$ 75/mês de um ambiente ECS/RDS ligado o tempo todo |

O que se perde em relação ao desenho de produção: sem alta disponibilidade (uma única instância), ~60–90s de espera no primeiro acesso após ociosidade, sem WAF por padrão (`enable_waf = false`, reversível), escala vertical em vez de horizontal.

## 6. Validação — teste de carga (F8, 2026-09-26)

**Como:** script k6 [`load/k6/regulacao.js`](../load/k6/regulacao.js) com o mix da seção 2 (≈ 80% leituras e 20% escritas: listas de encaminhamentos, fila por especialidade, agendamentos e vagas da unidade, unidades, estatísticas e posição pública na fila; cadastro de paciente → encaminhamento → regulação). Alvo: ambiente de homologação recriado só para o teste com a configuração de produção de uma task — **ECS Fargate 1 vCPU / 2 GB fixo em 1 task, RDS `db.t4g.medium`, CloudFront + WAF (IP do gerador liberado), ALB interno** —, depois destruído. Quatro patamares de 3 minutos a taxa constante; o gerador (k6 em contêiner) rodou fora da AWS, então a latência medida no cliente inclui a internet até `sa-east-1`. A referência do RNF-01 é a latência no ALB (`TargetResponseTime`).

| Carga | Requisições | Falhas | p95 no cliente (leitura / escrita) | p95 no ALB | CPU da task (média / máx.) | Memória da task (máx.) | CPU do RDS (máx.) | Conexões (máx.) |
|---|---|---|---|---|---|---|---|---|
| 50 req/s | 8.815 | 0% | 96 / 93 ms | 29 ms¹ | 29% / 76%¹ | 29% | 10% | 20 |
| 100 req/s | 18.188 | 0% | 72 / 70 ms | 27 ms | 27% / 36% | 30% | 15% | 20 |
| 200 req/s | 35.829 | 0% | 73 / 72 ms | 29 ms | 35% / 38% | 30% | 24% | 20 |
| **300 req/s** | **54.006** | **0%** | **75 / 72 ms** | **29 ms** | **40% / 50%** | **30%** | **33%** | **20** |

¹ O primeiro minuto do primeiro patamar coincidiu com o aquecimento da JVM (JIT) da task recém-criada: p95 de até 869 ms no ALB nesse minuto, estabilizando em ~29 ms em seguida. Em produção, o *health check* e o *rolling update* absorvem o aquecimento antes de a task receber tráfego pleno.

**Conclusões:**
- **RNF-01 comprovado** com folga: p95 de ~29 ms no ALB (meta: 300 ms para leitura e 500 ms para escrita) a 300 req/s em uma única task.
- **~300 req/s por task (estimativa de projeto) confirmado com sobra**: CPU média de 40% e banco a 33% nesse patamar — a saturação estimada fica acima de 500 req/s por task. Pico nacional de projeto (~1.750 req/s) ≈ 4 tasks a 60% de CPU; o autoscaling (alvo 60%, máximo 10) cobre o dobro disso.
- **Pool de conexões**: 20 conexões (Hikari) sustentaram 300 req/s com o banco a 33%; com 10 tasks, 200 conexões cabem no `db.t4g.medium` (limite ~400) — acima disso, `db.m7g.large` + réplica de leitura para os indicadores, como previsto.
- **Banco de produção inicial**: `db.t4g.medium` Multi-AZ mantido para o piloto estadual (< 20 req/s de pico); o cenário nacional exige a troca de classe antes de ~4 tasks sustentadas.
- Os eventos gerados pelo teste (entrada na fila, mensagens via SQS) foram processados sem acúmulo na fila e sem mensagens na DLQ.

**Repetir o teste** (contra qualquer URL, ex.: o demo):
```bash
docker run --rm -i -v "$PWD/load/k6:/scripts" -e BASE_URL=https://<cloudfront> \
  -e ADMIN_PASSWORD=... -e DEMO_PASSWORD=... -e RATE=100 -e DURATION=3m \
  grafana/k6 run --summary-export=/scripts/out/resultado.json /scripts/regulacao.js
```

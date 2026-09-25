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

## 3. Capacidade da solução

| Componente | Configuração | Capacidade / justificativa |
|---|---|---|
| API (ECS Fargate) | tasks de 1 vCPU / 2 GB, virtual threads, autoscaling por CPU (60%) e por requisições por task | estimativa de ~300 req/s por task nesta carga (I/O de banco) ⇒ pico nacional ≈ 5–6 tasks; máximo configurado 10 |
| Banco (RDS PostgreSQL) | produção inicial `db.t4g.medium` Multi-AZ; cenário nacional `db.m7g.large/xlarge` + réplica de leitura | escritas curtas e indexadas (≈ 1,5 mil linhas/s no pico nacional); índices parciais para a fila e a alocação |
| Fila (SQS) | padrão + DLQ, consumo concorrente limitado | vazão praticamente ilimitada; controla o ritmo de envio conforme os limites do provedor |
| Borda (CloudFront + WAF) | rate limit por IP nas rotas públicas | protege a consulta pública contra enumeração e picos anômalos |

**Decisão que elimina o maior pico:** a alocação é *push* — o sistema distribui as vagas à fila priorizada. Não há a corrida de "abertura de agenda" em que milhares de pacientes disputam as mesmas vagas ao mesmo tempo.

**Piloto estadual** (~20–30 mil agendamentos por dia útil ⇒ menos de 20 req/s de pico): 2 tasks e `db.t4g.medium` atendem com folga.

## 4. Custos estimados de operação

| Item | Homologação | Produção (piloto estadual) |
|---|---|---|
| Computação (Fargate) | ~US$ 10 (Spot, 1 task) | ~US$ 70 (2 tasks) |
| Banco (RDS) | ~US$ 20 (micro, single-AZ) | ~US$ 130 (medium Multi-AZ) |
| Rede (ALB, NAT, CloudFront) | ~US$ 25 | ~US$ 120 |
| WAF, segredos, logs e métricas | ~US$ 20 | ~US$ 40 |
| **Total de infraestrutura** | **~US$ 75/mês** | **~US$ 360/mês** |
| Mensageria (80% WhatsApp / 20% SMS) | — | ~R$ 0,29 por agendamento (≈ R$ 112 mil/mês no cenário moderado estadual) |

Custo de mensageria por atendimento recuperado: **~R$ 3,90** (redução de 7,5 p.p. no absenteísmo), frente a R$ 10,77–R$ 28,26 de valor de tabela SUS de cada consulta/exame desperdiçado — o custo real para o gestor é bem maior que a tabela.

## 5. Perfil demo (apresentação de baixo custo)

Não atende a demanda real do SUS — é só para ensaiar e gravar a apresentação com custo mínimo. Ver ADR-0012.

| Item | Configuração | Custo |
|---|---|---|
| Computação | 1 EC2 `t4g.medium` (app + PostgreSQL no mesmo host), desliga sozinha após 30 min sem acesso e religa sozinha no próximo acesso | ~US$ 0,0536/h só enquanto ligada — para ~10h de uso concentrado (ensaios + gravação): **~US$ 0,55** |
| Disco (EBS gp3, 20 GB) | cobrado o tempo todo, mesmo desligada | ~US$ 1,60/mês |
| Rede (CloudFront + 2 Lambdas pequenas + EventBridge Scheduler) | sem ALB, sem NAT, sem IP elástico | ~US$ 1–2/mês (majoritariamente dentro do nível gratuito) |
| Fila (SQS), Secrets Manager, logs | volume mínimo | ~US$ 1/mês |
| **Total** | | **~US$ 5–10 no mês da apresentação**, contra ~US$ 75/mês do hml (ECS/RDS) ligado o tempo todo |

O que se perde em relação ao hml/prod: sem alta disponibilidade (uma única instância), ~60–90s de espera no primeiro acesso após ociosidade, sem WAF por padrão (`enable_waf = false`, reversível), escala vertical em vez de horizontal.

## 6. Validação
O teste de carga (k6) contra o perfil hml/prod registra aqui RPS por task, latência p95/p99 e uso de CPU/conexões, e ajusta o máximo de tasks, o pool de conexões e a classe do banco de produção.

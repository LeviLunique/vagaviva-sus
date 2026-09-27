# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões em [SemVer](https://semver.org/lang/pt-BR/).

## [1.0.1] — 2026-09-26

### Adicionado
- Coleção Postman do vídeo de demonstração (`postman/vagaviva-demo-video.postman_collection.json`): preparação automatizada da massa e roteiro de gravação.
- `run-postman.sh` aceita outra coleção via `POSTMAN_COLLECTION`.
- README com a seção de demonstração ao vivo e os números de qualidade da versão.

### Alterado
- Ambiente de demonstração amostra 100% dos traces (volume baixo; o padrão da aplicação segue 10%).

## [1.0.0] — 2026-09-26

Primeira versão do MVP: regulação ambulatorial do SUS com confirmação ativa e reaproveitamento automático de vagas.

### Funcionalidades
- **Identidade e auditoria:** login com JWT RS256 e bloqueio por tentativas, perfis por papel e unidade, gestão de profissionais, trilha de auditoria de leituras de dados pessoais e ações.
- **Cadastros:** unidades de saúde com área de atendimento, especialidades e exames, pacientes com CNS/CPF validados e mascarados, busca por documento no corpo da requisição.
- **Regulação e transparência:** encaminhamento com protocolo, regulação com classe de risco, fila priorizada (risco → grupo prioritário → data de entrada), posição pública na fila e estatísticas sem dados pessoais.
- **Agenda e alocação:** publicação de vagas em lote, alocação automática da fila (concorrente e sem vaga ou paciente repetido), prazo de confirmação D-3, comparecimento e falta.
- **Confirmação ativa:** mensagens por SMS/WhatsApp (sandbox na demonstração) via outbox e SQS, link do paciente sem login para confirmar, cancelar ou desistir, lembretes, expiração do prazo e política de liberação de vagas.
- **Reaproveitamento de vagas:** encaixe em cascata por rodadas para quem aceita encaixe; o primeiro aceite vence (409 para os demais); recusa, expiração e vaga perdida.
- **Indicadores e observabilidade:** absenteísmo, confirmação, reaproveitamento, vagas perdidas, espera, fila por risco e custo de mensagens; métricas de negócio no CloudWatch, traces no X-Ray e logs com `traceId` e sem dados pessoais.
- **Hardening:** limite de corpo das requisições (413), cabeçalhos de segurança, CORS fechado, retenção de dados (mensagens 90 dias, auditoria 5 anos).

### Qualidade e operação
- TDD com cobertura de 98,8% das linhas e 92,7% dos branches; regras de arquitetura (Spring Modulith e ArchUnit) verificadas por testes; coleção Postman executada no CI e no ambiente.
- Teste de carga: 300 req/s em uma task de 1 vCPU com p95 de 29 ms no ALB (desenho de produção, ver `docs/capacity-planning.md`).
- Ambiente único de demonstração na AWS com CD a partir da `main` (ADR-0014).

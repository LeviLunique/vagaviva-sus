# Como contribuir

## Fluxo de branches (GitFlow)

| Branch | Origem | Destino (PR) | Uso |
|---|---|---|---|
| `main` | — | — | Produção. Só recebe `release/*` e `hotfix/*`. Cada merge gera a tag `vX.Y.Z`. |
| `develop` | `main` | — | Integração contínua; implantada automaticamente em homologação. Branch padrão. |
| `feature/<slug>` | `develop` | `develop` | Nova funcionalidade (ex.: `feature/f3-regulation-queue`). |
| `bugfix/<slug>` | `develop` | `develop` | Correção antes da release. |
| `chore/<slug>`, `docs/<slug>` | `develop` | `develop` | Manutenção e documentação. |
| `release/x.y.z` | `develop` | `main` (e back-merge em `develop`) | Estabilização da versão. |
| `hotfix/x.y.z` | `main` | `main` (e back-merge em `develop`) | Correção urgente em produção. |

Regras aplicadas automaticamente (workflow **PR Policy** + proteções de branch):
- `main` e `develop` não aceitam push direto nem force-push; todo merge passa por PR com todos os checks verdes.
- Merges usam *merge commit* (histórico do GitFlow preservado); a branch é removida após o merge.
- Todos os commits do PR devem ser de autoria do mantenedor do repositório, sem trailers de co-autoria.

## Commits (Conventional Commits)

```
<tipo>(<escopo opcional>): <descrição no imperativo, em português>
```
Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
Escopos sugeridos: `identity`, `catalog`, `patient`, `regulation`, `scheduling`, `reallocation`, `engagement`, `insights`, `audit`, `shared`, `infra`.
Exemplos: `feat(regulation): adiciona classificação de risco do encaminhamento` · `test(scheduling): cobre alocação concorrente com SKIP LOCKED`.
O título do PR segue o mesmo padrão.

## Definição de Pronto

1. Comportamento especificado por testes **escritos antes** da implementação (TDD: vermelho → verde → refatorar).
2. `./mvnw verify` verde — inclui regras de arquitetura (Modulith/ArchUnit) e cobertura ≥ **80%** de linhas e branches.
3. Endpoint novo ou alterado: OpenAPI anotado + requests e testes na coleção Postman + `./scripts/run-postman.sh` verde.
4. README e `docs/` atualizados (ADR para decisões arquiteturais novas).
5. Sem segredos, dados pessoais reais ou arquivos locais no diff.

## Convenções de código

- Arquitetura hexagonal por módulo: `domain` (Java puro) ← `application` (casos de uso e portas) ← `adapter` (web, persistência, integrações).
- Módulos conversam pela API pública do módulo (`<Modulo>Api`) ou por eventos de domínio; nunca acessam pacotes internos uns dos outros nem compartilham tabelas.
- SOLID de forma pragmática: uma responsabilidade por caso de uso, extensão por Strategy onde a regra varia (priorização da fila, canais de notificação, política de liberação de vagas), injeção por construtor.
- Nada de dados pessoais em logs, URLs ou mensagens além do mínimo necessário (LGPD).

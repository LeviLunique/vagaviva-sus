# ADR-0011 — GitFlow e Conventional Commits

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Entregas incrementais por fase com homologação contínua e versões estáveis para produção.

## Decisão
GitFlow (`feature/*` → `develop` → `release/*` → `main` + tags; `hotfix/*`), Conventional Commits, PRs obrigatórios com checks, merge commit e política de autoria validada no CI.

## Consequências
Histórico legível, deploy automático por branch/tag e rastreabilidade de versões.

## Alternativas consideradas
Trunk-based development (mais simples, porém sem a separação homologação/produção exigida pelo fluxo de releases do projeto).

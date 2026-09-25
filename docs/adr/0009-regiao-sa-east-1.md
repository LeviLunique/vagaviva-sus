# ADR-0009 — Região AWS sa-east-1 (São Paulo)

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Dados de saúde são sensíveis (LGPD) e entes públicos costumam exigir armazenamento no Brasil; usuários estão no Brasil.

## Decisão
Todos os recursos de dados e computação em `sa-east-1`; apenas o WAF de escopo CloudFront reside em `us-east-1` (exigência do serviço).

## Consequências
Residência de dados e menor latência; custo unitário maior que em regiões dos EUA.

## Alternativas consideradas
`us-east-1` (mais barato, mas com transferência internacional de dados sensíveis).

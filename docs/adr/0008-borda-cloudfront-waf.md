# ADR-0008 — Borda com CloudFront, WAF e VPC origin

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
A API precisa de HTTPS, proteção contra abuso das rotas públicas e não pode expor o balanceador diretamente; não há domínio próprio no MVP.

## Decisão
CloudFront com certificado padrão (*.cloudfront.net), AWS WAF (regras gerenciadas + rate limit por IP) e VPC origin apontando para um ALB interno.

## Consequências
HTTPS e proteção de borda sem domínio; nenhuma superfície pública além do CloudFront; domínio próprio pode ser adicionado depois (ACM + alias).

## Alternativas consideradas
ALB público com HTTP (sem TLS válido); API Gateway HTTP API + VPC Link (sem WAF nativo).

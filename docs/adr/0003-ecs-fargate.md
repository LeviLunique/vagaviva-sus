# ADR-0003 — Computação em ECS Fargate (ARM64)

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Carga contínua em horário comercial, transações com PostgreSQL e jobs agendados; necessidade de escalar horizontalmente sem administrar servidores.

## Decisão
Contêineres no ECS Fargate (Graviton/ARM64), atrás de ALB interno, com autoscaling por CPU e por requisições, deployment circuit breaker com rollback e Fargate Spot em homologação.

## Consequências
Pool de conexões estável com o banco, sem cold start em regime, mesma imagem local/CI/nuvem, custo previsível; não escala a zero (homologação pode ser pausada por script).

## Alternativas consideradas
AWS Lambda (cold start da JVM, explosão de conexões com o PostgreSQL sem RDS Proxy, limite de 15 min, modelo pouco natural para um processo de negócio com estado e jobs); EC2 (patching e operação de servidores); EKS (complexidade desnecessária).

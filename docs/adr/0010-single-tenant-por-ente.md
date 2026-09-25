# ADR-0010 — Implantação single-tenant por ente

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
A regulação é descentralizada (estados, municípios, consórcios); cada ente é controlador dos seus dados.

## Decisão
Uma implantação (conta/ambiente) por ente contratante, com a mesma imagem e o mesmo Terraform parametrizado.

## Consequências
Isolamento forte de dados e de desempenho, simples de auditar; multi-tenant compartilhado fica como evolução para entes pequenos.

## Alternativas consideradas
Multi-tenant com coluna de tenant desde o início (mais complexidade e risco de vazamento entre entes no MVP).

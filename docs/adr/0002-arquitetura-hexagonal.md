# ADR-0002 — Arquitetura hexagonal por módulo

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Regras de negócio (priorização da fila, prazos, política de liberação, cascata de ofertas) precisam ser testáveis sem infraestrutura e evoluir independentemente de frameworks e provedores (SMS/WhatsApp, banco).

## Decisão
Cada módulo com `domain` (Java puro), `application` (casos de uso e portas) e `adapter` (web, persistência, integrações). Regras garantidas por ArchUnit.

## Consequências
Domínio testado em milissegundos; troca de provedores por novos adapters (OCP/DIP); mais classes (entidade JPA separada do domínio e mapeadores).

## Alternativas consideradas
Camadas tradicionais com entidades JPA no domínio (mais rápido de escrever, mas acopla regras à persistência).

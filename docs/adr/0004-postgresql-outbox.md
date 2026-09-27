# ADR-0004 — PostgreSQL com outbox transacional

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Alocação de vagas e aceite de ofertas exigem atomicidade e prevenção de dupla alocação; a fila precisa de ordenação composta; indicadores exigem agregações.

## Decisão
RDS PostgreSQL 17 como banco único, com `SELECT ... FOR UPDATE SKIP LOCKED` para concorrência, índices parciais para a fila, e registro de publicação de eventos do Spring Modulith como outbox.

## Consequências
Consistência forte no núcleo e reentrega garantida de eventos; banco relacional exige atenção a índices e crescimento (particionamento previsto para auditoria/notificações).

## Alternativas consideradas
DynamoDB (bom para chave-valor em escala, mas ordenação composta da fila, transações multi-entidade e relatórios ficariam mais complexos); dual-write banco + fila sem outbox (risco de perder mensagens).

# ADR-0013 — Aceite de encaixe por atualização condicional da vaga

- **Status:** Aceita
- **Data:** 2026-09-26

## Contexto
Uma vaga liberada perto do atendimento é oferecida a vários pacientes ao mesmo tempo (lote de 3, RN-16), e o primeiro aceite válido fica com ela (RN-17). Os aceites chegam por links públicos e podem ser simultâneos, inclusive em instâncias diferentes da API. Uma vaga não pode ser entregue a dois pacientes, e os demais precisam de uma resposta clara ("vaga já preenchida", 409).

## Decisão
- O ponto de serialização é um único `UPDATE slot SET status = 'ALLOCATED' ... WHERE id = ? AND status = 'OPEN_FOR_OFFERS'` na agenda (`SchedulingApi.allocateFromOffer`). No PostgreSQL (READ COMMITTED), transações concorrentes esperam o commit da primeira, reavaliam o `WHERE` e alteram 0 linhas ⇒ `409 SLOT_ALREADY_FILLED`.
- A oferta é lida **sem trava**. O vencedor, na mesma transação, cria o agendamento já confirmado, agenda o encaminhamento, marca a própria oferta como `ACCEPTED` e as demais pendentes da vaga como `SUPERSEDED`.
- O índice único parcial `ux_slot_offer_one_accepted (slot_id, round) WHERE status = 'ACCEPTED'` é a última barreira.
- Rodadas seguintes só abrem quando nenhuma oferta da vaga está pendente; os candidatos de cada rodada são travados na fila com `FOR UPDATE SKIP LOCKED` (mesmo mecanismo da alocação, F4).

## Consequências
- Sem *deadlock*: travar a oferta com `FOR UPDATE` faria cada perdedor segurar a própria oferta esperando a vaga, enquanto o vencedor segura a vaga esperando para substituir essas ofertas.
- Correção verificada por `OfferRaceIT` (3 aceites simultâneos ⇒ 1 vencedor, 2 × 409).
- Dois cliques do mesmo paciente ao mesmo tempo: o segundo recebe 409 em vez de uma resposta idempotente — aceitável, porque a oferta já aparece como aceita no link.

## Alternativas consideradas
- Trava otimista (`version`) na vaga via JPA: exigiria tratar `OptimisticLockException` e retentar, sem ganho sobre o `UPDATE` condicional.
- Fila única de aceites (serializar em memória ou via SQS): acrescentaria latência e um componente a mais para um problema que o banco já resolve.

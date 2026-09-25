# ADR-0006 — Amazon SQS para envio de notificações

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
O envio de SMS/WhatsApp depende de provedores externos com limites de vazão e falhas transitórias; picos de lembretes não podem afetar a API.

## Decisão
Eventos de notificação externalizados para uma fila SQS padrão com DLQ (5 tentativas), consumidores idempotentes e retentativa/circuit breaker (Resilience4j). Localmente, ElasticMQ.

## Consequências
Isolamento de falhas e de picos, operação gerenciada e custo mínimo; entrega at-least-once exige idempotência (implementada por chave única).

## Alternativas consideradas
Kafka/MSK ou RabbitMQ/Amazon MQ (operação e custo maiores, recursos como replay e roteamento avançado não são necessários no MVP).

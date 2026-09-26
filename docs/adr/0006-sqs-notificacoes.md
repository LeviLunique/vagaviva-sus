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

## Atualização (F5, 2026-09-26)
- O módulo `spring-modulith-events-aws-sqs` não existe no Spring Modulith 2.x nem no Spring Cloud AWS 4.1. A externalização é feita por um *relay* (`SqsNotificationRelay`, `@ApplicationModuleListener` de `NotificationDispatchRequested`) que envia o id da notificação com o `SqsTemplate` — mesma garantia de *outbox*: o evento é gravado na transação da notificação e fica pendente até o envio à fila funcionar.
- Resilience4j é usado pela API programática (`RetryRegistry`/`CircuitBreakerRegistry` da instância `notification-channel`), sem AOP.
- Idempotência do consumidor: a notificação já `SENT` (ou `FAILED` após 5 tentativas) é ignorada; a criação é idempotente por marco do paciente.

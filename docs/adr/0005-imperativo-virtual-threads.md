# ADR-0005 — Modelo imperativo com virtual threads

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
A carga é dominada por I/O (JDBC, chamadas a provedores de mensagem).

## Decisão
Spring MVC imperativo com virtual threads do Java 25 (`spring.threads.virtual.enabled=true`).

## Consequências
Alta concorrência de I/O com código simples de ler, testar e depurar.

## Alternativas consideradas
Spring WebFlux/reativo (curva de aprendizado e complexidade de depuração sem ganho para este perfil de carga; JDBC é bloqueante).

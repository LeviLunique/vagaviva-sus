# ADR-0001 — Monólito modular com Spring Modulith

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
A solução tem vários bounded contexts (regulação, agenda, engajamento, reaproveitamento, indicadores), um único time e demanda projetada de até ~1.400 leituras/s e ~350 escritas/s por implantação (ver capacity-planning).

## Decisão
Um único deployable organizado em módulos com fronteiras verificadas por teste (Spring Modulith `verify()`), comunicação por API pública do módulo ou por eventos de domínio, e tabelas exclusivas por módulo (sem FK entre módulos).

## Consequências
Deploy, observabilidade e transações simples; escala horizontal do processo inteiro; módulos extraíveis para serviços quando houver necessidade real (ex.: engajamento com volume de mensagens muito maior).

## Alternativas consideradas
Microsserviços desde o início (custo operacional, consistência distribuída e risco de 'monólito distribuído' sem benefício no volume atual); monólito sem fronteiras (acoplamento crescente).

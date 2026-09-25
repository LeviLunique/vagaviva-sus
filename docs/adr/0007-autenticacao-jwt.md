# ADR-0007 — Autenticação própria com JWT RS256

- **Status:** Aceita
- **Data:** 2026-09-24

## Contexto
Profissionais das secretarias precisam de autenticação e papéis; o MVP deve rodar localmente e em CI sem dependência externa.

## Decisão
A API emite JWT RS256 (Spring Security OAuth2 Resource Server + Nimbus), chave privada no Secrets Manager, BCrypt, bloqueio por tentativas. Pacientes não têm conta: usam links com token opaco de uso restrito.

## Consequências
Simples de operar e testar; evolução planejada para IdP gerenciado (Cognito/gov.br) sem alterar os recursos da API, pois eles só validam JWT.

## Alternativas consideradas
Amazon Cognito/Keycloak já no MVP (dependência externa em testes locais e mais integração sem ganho funcional imediato).

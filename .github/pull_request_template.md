## O que muda
<!-- Resumo objetivo da mudança e da fase do roadmap atendida. -->

## Tipo
- [ ] feat
- [ ] fix
- [ ] refactor / chore / docs / ci / build / test

## Checklist (Definition of Done)
- [ ] Testes escritos antes da implementação (TDD) — unitários e de integração
- [ ] `./mvnw verify` verde localmente (cobertura ≥ 80% linhas e branches)
- [ ] Endpoints novos/alterados refletidos na coleção Postman e passando em `./scripts/run-postman.sh`
- [ ] Contrato OpenAPI atualizado (anotações `@Operation`/`@ApiResponse`)
- [ ] Migrations Flyway novas (nunca editar migrations já mergeadas)
- [ ] README atualizado quando houver novo endpoint, variável de ambiente ou comando
- [ ] Sem segredos, dados pessoais reais ou arquivos locais no diff

## Como testar
<!-- Passos, requests do Postman ou comandos. -->

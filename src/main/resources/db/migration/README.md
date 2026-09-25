# Migrations (Flyway)

- Local: `src/main/resources/db/migration`, executadas no startup (`spring.flyway.enabled=true`).
- Nomenclatura: `V<n>__<descricao_em_snake_case>.sql` (ex.: `V1__create_identity_schema.sql`).
- Uma migration por mudança de schema; nunca editar uma migration já mergeada em `develop`.
- Tipos: `uuid` para ids, `timestamptz` para datas, `varchar` com tamanho explícito, `CHECK` para enums.
- Todo índice criado deve ser justificado por uma consulta (use `EXPLAIN ANALYZE` na revisão).
- Dados de demonstração ficam em `src/main/resources/db/seed` (`R__seed_demo_*.sql`), incluídos em `spring.flyway.locations` apenas nos perfis `local` e `hml`.

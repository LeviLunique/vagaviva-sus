# ADR-0014 — Ambiente único de demonstração

- **Status:** Aceita
- **Data:** 2026-09-26
- **Substitui:** o uso de ECS Fargate/RDS da ADR-0003 como ambiente provisionado (a ADR-0003 fica como desenho de referência)

## Contexto
O VagaViva é o MVP de um trabalho de pós-graduação/hackathon. Não haverá operação real em um ente: o que importa é demonstrar a aplicação completa funcionando. O desenho de produção (ECS Fargate + RDS Multi-AZ + ALB + WAF, ADR-0003/0008) foi implementado em Terraform e validado na F8 com teste de carga (RNF-01 comprovado: p95 ≈ 29 ms no ALB a 300 req/s em uma task de 1 vCPU), mas manter hml e prod custaria ~US$ 75 e ~US$ 360 por mês sem uso.

## Decisão
- O ambiente de **demonstração** (ADR-0012: CloudFront + uma EC2 com API e PostgreSQL) é o **único** ambiente, com **todas** as funcionalidades do MVP — inclusive a observabilidade da F7: coletor ADOT ao lado da aplicação, métricas de negócio no CloudWatch (namespace `VagaViva`), painel, alarmes e traces no X-Ray.
- O código Terraform de hml/prod (`infra/stack`, `infra/envs/hml`, `infra/envs/prod`) e os scripts de ECS saem do repositório. O desenho e os números do teste de carga continuam documentados (`docs/architecture.md`, `docs/capacity-planning.md`).
- **CD**: merge em `main` publica no demo (workflow `Deploy`: build ARM64 → ECR → `scripts/aws/demo-deploy.sh` via SSM → health → Newman), com role OIDC `vagaviva-gha-deploy-demo` restrita à instância do demo, aos parâmetros `/vagaviva/demo/*` e ao ECR.
- O compose e a configuração do coletor ficam em parâmetros do SSM, lidos a cada boot: mudar o compose não recria a instância (o que apagaria o banco de demonstração).
- WAF segue desligado no demo (custo fixo); os controles de aplicação (limite de corpo, cabeçalhos, escopo por unidade, auditoria) valem igual.

## Consequências
- Custo mensal de poucos dólares (a instância só fica ligada durante o uso).
- Sem alta disponibilidade nem escala horizontal no ambiente que existe; a capacidade de produção é argumentada pelo teste de carga da F8, não por um ambiente ativo.
- Um único ambiente recebe o código de `main`: o que chega lá passou pelo CI completo (testes, cobertura, Newman no Compose, segurança) e pela revisão da release.

## Alternativas consideradas
- **Manter o Terraform de hml/prod como referência não provisionada:** preserva a IaC do desenho de produção, mas mantém no repositório código sem ambiente para exercitá-lo.
- **Recriar o hml só para cada demonstração:** ~20 min de provisionamento e custo por hora maior, sem ganho funcional para a apresentação.

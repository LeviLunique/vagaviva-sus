# ADR-0012 — Perfil demo: EC2 única com liga/desliga automático

- **Status:** Aceita
- **Data:** 2026-09-25

## Contexto
O perfil hml/prod (ADR-0003, ADR-0004) usa ECS Fargate + RDS Multi-AZ — o desenho correto para a demanda real do SUS (SPEC §11), mas com custo fixo (~US$ 75/mês em hml) mesmo quando ninguém está usando o sistema, o que não se justifica para uma apresentação de pós-graduação/hackathon com uso concentrado em poucos dias (ensaios + gravação + banca).

## Decisão
Criar um segundo perfil de infraestrutura (`infra/envs/demo`), com arquitetura diferente e propósito explícito de demonstração, não de produção:
- Uma única **EC2 t4g.medium** roda a API e o **PostgreSQL juntos**, via Docker Compose.
- Sem ALB, sem NAT Gateway, sem IP elástico: a **CloudFront alcança a instância pela rede interna da VPC** (VPC origin apontando direto para o ARN da EC2 — o mesmo mecanismo de segurança já usado para o ALB do hml, ADR-0008), sem expor a porta 80 publicamente.
- A instância **desliga sozinha após 30 minutos sem requisições** na CloudFront (função Lambda agendada, lê a métrica `Requests` do CloudFront) e **liga sozinha no primeiro acesso seguinte**: a CloudFront usa um *origin group* com a EC2 como origem primária e uma segunda função Lambda (protegida por Origin Access Control/SigV4, sem endpoint público) como origem de contingência — ela liga a instância e devolve uma página de espera que se recarrega sozinha até a aplicação responder.
- Fila SQS real (não simulada) para manter a mensageria de verdade.
- Segredos, contêiner e imagem seguem o mesmo padrão do hml/prod (Secrets Manager, ECR, tag controlada por parâmetro SSM).

## Consequências
- Custo estimado: **~US$ 10–15/mês** com uso concentrado em poucos dias (contra ~US$ 75/mês do hml ligado o tempo todo) — ver `docs/capacity-planning.md`.
- Primeiro acesso após ociosidade demora ~60–90 segundos (boot da instância + banco + aplicação).
- Sem alta disponibilidade: uma única instância, sem failover automático de infraestrutura; recuperação manual em caso de falha da instância (minutos).
- Escala verticalmente (trocar o tipo da instância), não horizontalmente — adequado para demonstração, não para a carga real projetada no SPEC §11.
- O desenho de produção (ECS Fargate + RDS Multi-AZ) permanece no repositório, documentado e testado (`infra/stack`, `infra/envs/hml`, `infra/envs/prod`), pronto para ser reaplicado quando o sistema for usado de verdade.
- O ambiente `hml` foi desprovisionado (`terraform destroy`) depois que o perfil demo foi validado, para eliminar o custo duplicado durante o período de apresentação; a variável `AWS_DEPLOY_ENABLED` do GitHub foi desligada para o pipeline automático de deploy não falhar tentando atualizar um ambiente que não existe mais.

## Lições da implementação
- **CloudFront não aceita `POST`/`PUT`/`PATCH`/`DELETE` num comportamento associado a um *origin group*** (não dá para reenviar uma escrita não confirmada a outra origem). Solução: só as rotas de entrada GET (Swagger, health, `/p/*`) passam pelo grupo com religamento automático; o restante da API (a maioria, com métodos de escrita) vai direto para a EC2, sem esse religamento — por isso o fluxo de demonstração sempre abre uma rota de entrada primeiro.
- **Origin Access Control (SigV4) numa Lambda usada como membro de um *origin group* de failover não funcionou de forma confiável** (a CloudFront devolvia 403 mesmo com a permissão e o OAC corretos). A função "acordar" usa, em vez disso, uma URL pública (`authorization_type = NONE`) protegida por um cabeçalho secreto gerado pelo Terraform (nunca aparece no código-fonte) e só injetado pela CloudFront nessa origem específica.
- **Desde outubro de 2025, toda Function URL exige duas permissões** — `lambda:InvokeFunctionUrl` **e** `lambda:InvokeFunction` (esta última com a condição `invoked_via_function_url`) — sem a segunda, a própria URL responde 403 antes de chamar o código, mesmo com `AuthType = NONE` e o cabeçalho correto.
- **`ec2:DescribeInstances` não suporta permissão em nível de recurso** (é uma consulta, não uma ação sobre um recurso específico): uma condição por `ec2:ResourceTag` nela é silenciosamente ignorada (`implicitDeny`), mesmo com a tag certa no recurso. A política final usa `Resource = "*"` sem condição para `DescribeInstances`, e o ARN exato da instância (sem condição de tag) para `StartInstances`/`StopInstances` — mais preciso e mais simples de depurar.
- O perfil do PostgreSQL padrão (`aws`) força `sslmode=require` (correto para o RDS do hml/prod); o container de banco do perfil demo não tem TLS configurado. Criado `application-demo.yml` sobrescrevendo a URL do banco sem SSL — com `SPRING_PROFILES_ACTIVE=aws,demo`, o perfil citado por último tem precedência para a mesma chave.

## Alternativas consideradas
- **Manter hml (ECS/RDS) ligado só durante os ensaios/gravação e desligar manualmente**: exigiria lembrar de ligar/desligar o RDS e o serviço ECS toda vez (scripts `pause.sh`/`resume.sh` já existiam para isso) e ainda pagaria pelo ALB e pelo disco do RDS o tempo todo; não tem o efeito "religa sozinho ao acessar o link", que é o que o usuário pediu explicitamente.
- **RDS "Serverless v2" no lugar do Postgres em contêiner**: continua cobrando uma capacidade mínima mesmo ociosa e não desliga sozinho até zero; não elimina o custo fixo do jeito que uma EC2 desligada elimina.
- **AWS Lightsail**: mais simples de configurar, mas não integra nativamente com CloudFront VPC origin, ECR, Secrets Manager e IAM do jeito que o resto do projeto já usa — trocaria consistência arquitetural por conveniência pontual.
